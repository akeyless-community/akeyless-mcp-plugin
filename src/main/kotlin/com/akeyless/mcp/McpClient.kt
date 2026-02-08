package com.akeyless.mcp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MCP Client for communicating with Akeyless MCP server
 * Uses stdio transport to communicate with the MCP server process
 */
class McpClient {
    private val logger = thisLogger()
    private val gson = Gson()
    private var process: Process? = null
    private var requestIdCounter = 0
    @Volatile
    private var lastConnectionError: String? = null
    private val requestMutex = Mutex() // Serialize requests so responses match
    
    companion object {
        private const val MCP_VERSION = "2024-11-05"
    }
    
    init {
        // Give the process a moment to start before sending requests
        // This is handled by the coroutine context delay if needed
    }
    
    /**
     * Resolve the full path to a command
     */
    private fun resolveCommandPath(command: String): String {
        // If it's already an absolute path, use it as-is
        if (command.startsWith("/")) {
            return command
        }
        
        // Try to find the command in PATH
        val pathEnv = System.getenv("PATH") ?: ""
        val paths = pathEnv.split(":")
        
        // Also check common locations (include Homebrew on Apple Silicon)
        val commonPaths = listOf(
            "/opt/homebrew/bin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
            System.getProperty("user.home") + "/.local/bin",
            System.getProperty("user.home") + "/bin"
        )
        
        val allPaths = paths + commonPaths
        
        for (path in allPaths) {
            val fullPath = java.io.File(path, command)
            if (fullPath.exists() && fullPath.canExecute()) {
                return fullPath.absolutePath
            }
        }
        
        // If not found, return the original command (will fail with a better error)
        return command
    }
    
    /**
     * Start the MCP server process
     * @param serverCommand The command to execute (e.g., "akeyless" or "/usr/local/bin/akeyless")
     * @param serverArgs Optional arguments for the command (e.g., "mcp --gateway-url https://api.akeyless.io --profile saml")
     * @param workingDirectory Optional working directory
     */
    fun getLastConnectionError(): String? = lastConnectionError

    suspend fun connect(serverCommand: String, serverArgs: String? = null, workingDirectory: String? = null): Boolean = withContext(Dispatchers.IO) {
        lastConnectionError = null
        try {
            // Resolve the command path
            val resolvedCommand = resolveCommandPath(serverCommand)
            logger.info("Resolved command: $resolvedCommand")
            
            val commandParts = mutableListOf(resolvedCommand)
            if (serverArgs != null && serverArgs.isNotEmpty()) {
                commandParts.addAll(serverArgs.split("\\s+".toRegex()))
            }
            
            // Auto-detect auth flags from the default akeyless profile if not already specified
            val argsStr = commandParts.joinToString(" ")
            if (!argsStr.contains("--access-type") && !argsStr.contains("--access-id")) {
                val profileAuth = readAkeylessProfileAuth()
                if (profileAuth != null) {
                    logger.info("Auto-injecting auth from default profile: access_type=${profileAuth.first}, access_id=${profileAuth.second}")
                    commandParts.add("--access-type")
                    commandParts.add(profileAuth.first)
                    commandParts.add("--access-id")
                    commandParts.add(profileAuth.second)
                }
            }
            
            logger.info("Starting MCP server: ${commandParts.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(commandParts)
            
            if (workingDirectory != null) {
                processBuilder.directory(File(workingDirectory))
            }
            
            // Set up environment variables: inherit full environment so CLI has auth/config
            val env = processBuilder.environment()
            env.putAll(System.getenv())
            // Ensure PATH includes Homebrew and common locations (IDE may have minimal PATH)
            val currentPath = env["PATH"] ?: ""
            val pathAdditions = listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin")
                .filter { !currentPath.contains(it) }
            if (pathAdditions.isNotEmpty()) {
                env["PATH"] = (pathAdditions.joinToString(":") + ":" + currentPath).trimEnd(':')
            }
            env["HOME"] = System.getProperty("user.home")
            
            processBuilder.redirectErrorStream(false)
            processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE)
            
            logger.info("Starting MCP process with environment: PATH=${env["PATH"]}, HOME=${env["HOME"]}, DISPLAY=${env["DISPLAY"]}")
            val currentProcess = processBuilder.start()
            process = currentProcess
            
            // Give the process a moment to start
            delay(500)
            
            // Check if process is still alive
            if (!currentProcess.isAlive) {
                logger.error("MCP server process died immediately after start")
                val exitCode = try { currentProcess.exitValue() } catch (e: Exception) { -1 }
                val stderr = readProcessStderrAndReturn()
                lastConnectionError = stderr.ifEmpty { "Exit code $exitCode" }
                logger.error("Process exit code: $exitCode, stderr: $stderr")
                return@withContext false
            }
            
            // Monitor stderr in background (for auth prompts etc.)
            monitorStderrForAuth()
            
            // Send initialize request immediately
            val initRequest = createInitRequest()
            logger.info("Sending initialization request")
            sendRequest(initRequest)
            
            // Read initialization response - use long timeout because SAML auth 
            // may open a browser and the user needs time to log in.
            // The reader will skip non-JSON-RPC output (like credential dumps).
            logger.info("Waiting for initialization response (timeout: 120 seconds, SAML auth may require browser login)...")
            val initResponse = readResponseWithTimeout(120_000)
            logger.info("Init response: ${initResponse?.let { gson.toJson(it) } ?: "null"}")
            
            if (initResponse != null && initResponse.has("result")) {
                logger.info("MCP server initialized successfully")
                sendInitializedNotification()
                delay(200)
                return@withContext true
            } else if (initResponse != null && initResponse.has("error")) {
                val error = initResponse.getAsJsonObject("error")
                lastConnectionError = error.get("message")?.asString ?: gson.toJson(error)
                logger.error("MCP init error: $lastConnectionError")
                return@withContext false
            } else {
                // No valid init response
                val stderr = readProcessStderrAndReturn()
                lastConnectionError = stderr.ifEmpty { "No response from MCP server (timed out or auth failed)" }
                logger.error("No init response. stderr: $stderr")
                if (currentProcess.isAlive) {
                    logger.warn("Process still alive but no init response")
                }
                return@withContext false
            }
        } catch (e: Exception) {
            logger.error("Error connecting to MCP server", e)
            lastConnectionError = e.message ?: e.toString()
            return@withContext false
        }
    }
    
    /**
     * Disconnect from MCP server
     */
    fun disconnect() {
        stdinReader = null
        process?.destroy()
        process = null
    }
    
    /**
     * Call an MCP tool
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any> = emptyMap()): JsonObject? = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            try {
                val request = createToolCallRequest(toolName, arguments)
                sendRequest(request)
                readResponse()
            } catch (e: Exception) {
                logger.error("Error calling tool $toolName", e)
                null
            }
        }
    }
    
    /**
     * List available tools
     */
    suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        requestMutex.withLock {
        try {
            val request = createListToolsRequest()
            sendRequest(request)
            val response = readResponse()
            
            if (response != null && response.has("result")) {
                val result = response.getAsJsonObject("result")
                val toolsArray = result.get("tools")
                
                if (toolsArray != null && toolsArray.isJsonArray) {
                    val tools = toolsArray.asJsonArray
                    return@withContext tools.mapNotNull { toolElement ->
                        if (!toolElement.isJsonObject) {
                            return@mapNotNull null
                        }
                        val toolObj = toolElement.asJsonObject
                        val name = toolObj.get("name")?.asString
                        if (name == null) {
                            return@mapNotNull null
                        }
                        McpTool(
                            name = name,
                            description = toolObj.get("description")?.asString,
                            arguments = toolObj.getAsJsonObject("inputSchema")?.getAsJsonObject("properties")
                        )
                    }
                } else {
                    logger.warn("Response does not contain a 'tools' array: $result")
                }
            }
            
            emptyList()
        } catch (e: Exception) {
            logger.error("Error listing tools", e)
            emptyList()
        }
        } // end requestMutex.withLock
    }
    
    private fun createInitRequest(): JsonObject {
        val request = JsonObject()
        request.addProperty("jsonrpc", "2.0")
        request.addProperty("id", getNextRequestId())
        request.addProperty("method", "initialize")
        
        val params = JsonObject()
        params.addProperty("protocolVersion", MCP_VERSION)
        params.add("capabilities", JsonObject())
        val clientInfo = JsonObject()
        clientInfo.addProperty("name", "jetbrains-akeyless-plugin")
        clientInfo.addProperty("version", "1.0.0")
        params.add("clientInfo", clientInfo)
        
        request.add("params", params)
        return request
    }
    
    private fun sendInitializedNotification() {
        try {
            val notification = JsonObject()
            notification.addProperty("jsonrpc", "2.0")
            notification.addProperty("method", "notifications/initialized")
            // Notifications don't have an id
            sendRequest(notification)
            logger.info("Sent initialized notification")
        } catch (e: Exception) {
            logger.error("Error sending initialized notification", e)
        }
    }
    
    private fun createListToolsRequest(): JsonObject {
        val request = JsonObject()
        request.addProperty("jsonrpc", "2.0")
        request.addProperty("id", getNextRequestId())
        request.addProperty("method", "tools/list")
        return request
    }
    
    private fun createToolCallRequest(toolName: String, arguments: Map<String, Any>): JsonObject {
        val request = JsonObject()
        request.addProperty("jsonrpc", "2.0")
        request.addProperty("id", getNextRequestId())
        request.addProperty("method", "tools/call")
        
        val params = JsonObject()
        params.addProperty("name", toolName)
        
        val argsJson = JsonObject()
        arguments.forEach { (key, value) ->
            when (value) {
                is String -> argsJson.addProperty(key, value)
                is Number -> argsJson.addProperty(key, value)
                is Boolean -> argsJson.addProperty(key, value)
                is List<*> -> {
                    val array = com.google.gson.JsonArray()
                    value.forEach { item ->
                        when (item) {
                            is String -> array.add(item)
                            is Number -> array.add(item)
                            else -> array.add(gson.toJsonTree(item))
                        }
                    }
                    argsJson.add(key, array)
                }
                else -> argsJson.add(key, gson.toJsonTree(value))
            }
        }
        params.add("arguments", argsJson)
        
        request.add("params", params)
        return request
    }
    
    private fun sendRequest(request: JsonObject) {
        val currentProcess = this.process ?: throw IllegalStateException("MCP server not connected")
        val outputStream = currentProcess.outputStream
        val writer = BufferedWriter(OutputStreamWriter(outputStream))
        
        val requestStr = gson.toJson(request)
        logger.info("Sending MCP request: $requestStr")
        
        // Akeyless MCP server accepts raw JSON (tested manually)
        // But we'll try both formats - first raw JSON, fallback to Content-Length if needed
        // For now, send raw JSON since that's what works with akeyless
        writer.write(requestStr)
        writer.newLine() // Add newline to signal end of message
        writer.flush()
        logger.info("Request sent (raw JSON format)")
    }
    
    private var stdinReader: BufferedReader? = null

    private fun getOrCreateReader(): BufferedReader {
        val currentProcess = this.process ?: throw IllegalStateException("MCP server not connected")
        if (stdinReader == null) {
            stdinReader = BufferedReader(InputStreamReader(currentProcess.inputStream))
        }
        return stdinReader!!
    }

    private suspend fun readResponse(): JsonObject? {
        return readResponseWithTimeout(10000)
    }
    
    /**
     * Read lines from stdout until we find a valid JSON-RPC message (has "jsonrpc" field).
     * Skips non-JSON lines and non-JSON-RPC JSON (like credential dumps from akeyless).
     */
    private suspend fun readResponseWithTimeout(timeoutMs: Long?): JsonObject? {
        val currentProcess = this.process ?: return null
        val reader = getOrCreateReader()
        val deadline = if (timeoutMs != null) System.currentTimeMillis() + timeoutMs else Long.MAX_VALUE
        
        try {
            while (System.currentTimeMillis() < deadline) {
                // Wait for data to be available
                while (currentProcess.inputStream.available() == 0) {
                    if (System.currentTimeMillis() >= deadline) {
                        logger.error("Timeout waiting for MCP response after ${timeoutMs}ms")
                        return null
                    }
                    if (!currentProcess.isAlive) {
                        logger.error("Process died while waiting for response")
                        readProcessStderr()
                        return null
                    }
                    delay(50)
                }
                
                val line = reader.readLine()
                if (line == null) {
                    logger.error("Unexpected end of stream from MCP server")
                    return null
                }
                
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                // Skip non-JSON lines
                if (!trimmed.startsWith("{")) {
                    logger.info("Skipping non-JSON line from MCP stdout: ${trimmed.take(100)}...")
                    continue
                }
                
                // Try to parse as JSON
                val jsonObj = try {
                    JsonParser.parseString(trimmed).asJsonObject
                } catch (e: Exception) {
                    logger.info("Skipping unparseable JSON line: ${trimmed.take(100)}...")
                    continue
                }
                
                // Check if it's a JSON-RPC message (has "jsonrpc" field)
                if (jsonObj.has("jsonrpc")) {
                    logger.info("Received JSON-RPC message: ${trimmed.take(200)}...")
                    return jsonObj
                }
                
                // Not a JSON-RPC message (e.g. credential dump) - skip it
                logger.info("Skipping non-JSON-RPC JSON (keys: ${jsonObj.keySet().take(5)})")
            }
            
            logger.error("Timeout: no JSON-RPC response received within ${timeoutMs}ms")
            return null
        } catch (e: Exception) {
            logger.error("Error reading MCP response", e)
            readProcessStderr()
            return null
        }
    }
    
    private fun readProcessStderr() {
        readProcessStderrAndReturn()
    }

    /**
     * Read process stderr and return it (for surfacing to user). Also logs.
     */
    private fun readProcessStderrAndReturn(): String {
        return try {
            val currentProcess = this.process ?: return ""
            val errorStream = currentProcess.errorStream ?: return ""
            val errorReader = BufferedReader(InputStreamReader(errorStream))
            val errorLines = mutableListOf<String>()
            var count = 0
            var line: String? = errorReader.readLine()
            while (count < 20 && line != null) {
                errorLines.add(line)
                count++
                line = errorReader.readLine()
            }
            errorLines.joinToString("\n").trim()
        } catch (e: Exception) {
            logger.error("Error reading process stderr", e)
            ""
        }
    }
    
    /**
     * Monitor stderr in the background for authentication-related messages
     */
    private fun monitorStderrForAuth() {
        val currentProcess = this.process ?: return
        val errorStream = currentProcess.errorStream
        
        // Start a coroutine to monitor stderr
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(errorStream))
                var line: String? = reader.readLine()
                while (currentProcess.isAlive && line != null) {
                    val trimmed = line.trim()
                    logger.info("MCP stderr: $trimmed")
                    
                    // Check for authentication-related messages
                    if (trimmed.contains("browser", ignoreCase = true) || 
                        trimmed.contains("authentication", ignoreCase = true) ||
                        trimmed.contains("login", ignoreCase = true) ||
                        trimmed.contains("auth", ignoreCase = true)) {
                        logger.warn("Authentication may be required: $trimmed")
                    }
                    
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                // Stream closed or process ended
                logger.debug("Stderr monitoring ended: ${e.message}")
            }
        }
    }
    
    /**
     * Read access_type and access_id from the default akeyless profile (~/.akeyless/profiles/default.toml).
     * Returns Pair(access_type, access_id) or null if not found.
     */
    private fun readAkeylessProfileAuth(): Pair<String, String>? {
        try {
            val home = System.getProperty("user.home") ?: return null
            val profileFile = File("$home/.akeyless/profiles/default.toml")
            if (!profileFile.exists()) return null
            
            var accessType: String? = null
            var accessId: String? = null
            
            profileFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("access_type")) {
                    accessType = trimmed.substringAfter("=").trim().trim('\'', '"', ' ')
                } else if (trimmed.startsWith("access_id")) {
                    accessId = trimmed.substringAfter("=").trim().trim('\'', '"', ' ')
                }
            }
            
            val at = accessType
            val ai = accessId
            if (at != null && at.isNotEmpty() && ai != null && ai.isNotEmpty()) {
                return Pair(at, ai)
            }
            return null
        } catch (e: Exception) {
            logger.warn("Could not read akeyless profile: ${e.message}")
            return null
        }
    }

    private fun getNextRequestId(): Int {
        return ++requestIdCounter
    }
    
    fun isConnected(): Boolean {
        val currentProcess = this.process
        return currentProcess != null && currentProcess.isAlive
    }
}

/**
 * Represents an MCP tool
 */
data class McpTool(
    val name: String,
    val description: String?,
    val arguments: JsonObject?
)
