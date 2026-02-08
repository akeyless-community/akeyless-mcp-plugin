package com.akeyless.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Application-level service for managing MCP client connection
 */
@Service
class McpClientService {
    private val logger = thisLogger()
    private val client = McpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun getClient(): McpClient = client

    fun getLastConnectionError(): String? = client.getLastConnectionError()
    
    fun connect(serverCommand: String, serverArgs: String? = null, workingDirectory: String? = null, callback: (Boolean) -> Unit) {
        scope.launch {
            val connected = withTimeoutOrNull(150_000) { // 150 second timeout (SAML auth may require browser login)
                client.connect(serverCommand, serverArgs, workingDirectory)
            } ?: false // Return false if timeout
            
            ApplicationManager.getApplication().invokeLater {
                callback(connected)
            }
        }
    }
    
    fun disconnect() {
        client.disconnect()
    }
    
    fun isConnected(): Boolean {
        return client.isConnected()
    }
    
    companion object {
        fun getInstance(): McpClientService {
            return ApplicationManager.getApplication().getService(McpClientService::class.java)
        }
    }
}
