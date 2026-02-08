package com.akeyless.mcp.ui

import com.akeyless.mcp.McpClientService
import com.akeyless.mcp.McpTool
import com.akeyless.mcp.settings.AkeylessMcpSettings
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

/**
 * Main tool window panel for Akeyless MCP
 */
class AkeylessMcpToolWindowPanel(private val project: Project) {
    private val logger = thisLogger()
    private val gson = Gson()
    private val settings = AkeylessMcpSettings.getInstance()
    private val clientService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(McpClientService::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)
    
    // Store tool data for the tools panel
    private val toolDataMap = mutableMapOf<String, McpTool>()
    private var toolsModel: DefaultListModel<String>? = null
    
    private val mainPanel = JPanel(BorderLayout())
    private val statusLabel = JBLabel("Not connected")
    private val itemsTree = Tree()
    private val rootNode = DefaultMutableTreeNode("Akeyless Items")
    private val treeModel = DefaultTreeModel(rootNode)
    private val detailsPanel = JPanel()
    private val detailsTextArea = JTextArea()
    
    init {
        itemsTree.model = treeModel
        itemsTree.cellRenderer = AkeylessItemTreeCellRenderer()
        itemsTree.isRootVisible = false
        rootNode.add(DefaultMutableTreeNode("(Connect and refresh to load items)"))
        treeModel.reload()
        
        // Add tree selection listener
        itemsTree.selectionModel.addTreeSelectionListener {
            val node = itemsTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            when (val userObject = node?.userObject) {
                is AkeylessItem -> showItemDetails(userObject)
                is FolderItem -> {
                    detailsTextArea.text = "Folder: ${userObject.fullPath}\n\nClick on an item inside to see its details."
                }
            }
        }
        
        setupUI()
        checkConnection()
        
        // Start periodic connection status check
        startPeriodicStatusCheck()
    }
    
    private fun startPeriodicStatusCheck() {
        scope.launch {
            while (true) {
                delay(2000) // Check every 2 seconds
                val isConnected = clientService.isConnected()
                ApplicationManager.getApplication().invokeLater {
                    if (isConnected && (statusLabel.text == "Connecting..." || statusLabel.text.contains("timeout"))) {
                        updateConnectionStatus(true)
                    } else if (!isConnected && statusLabel.text == "Connected") {
                        updateConnectionStatus(false)
                    }
                }
            }
        }
    }
    
    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            statusLabel.text = "Connected"
            statusLabel.icon = AllIcons.General.InspectionsOK
            // Only refresh if we haven't loaded items yet
            val root = treeModel.root as? DefaultMutableTreeNode
            if (root == null || root.childCount == 0) {
                refreshItems()
            }
            // Refresh tools list if model exists
            toolsModel?.let { loadAvailableToolsIntoList(it) }
        } else {
            statusLabel.text = "Not connected - Configure in Settings"
            statusLabel.icon = AllIcons.General.Error
        }
    }
    
    private fun setupUI() {
        // Status bar
        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = JBUI.Borders.empty(5)
        statusPanel.add(statusLabel, BorderLayout.WEST)
        mainPanel.add(statusPanel, BorderLayout.NORTH)
        
        // Tabbed pane for different views
        val tabbedPane = JTabbedPane()
        
        // Items tab with split pane
        val itemsSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        val treeScrollPane = JBScrollPane(itemsTree)
        treeScrollPane.border = JBUI.Borders.empty(5)
        itemsSplitPane.leftComponent = treeScrollPane
        
        detailsTextArea.isEditable = false
        detailsTextArea.font = detailsTextArea.font.deriveFont(12f)
        detailsTextArea.text = "Select an item from the tree to see details.\n\nConnect (toolbar) and refresh if the tree is empty."
        val detailsScrollPane = JBScrollPane(detailsTextArea)
        detailsScrollPane.border = JBUI.Borders.empty(5)
        itemsSplitPane.rightComponent = detailsScrollPane
        itemsSplitPane.dividerLocation = 300
        itemsSplitPane.isOneTouchExpandable = true
        
        tabbedPane.addTab("Items", itemsSplitPane)
        
        // Tools tab - show all available MCP tools
        val toolsPanel = createToolsPanel()
        tabbedPane.addTab("MCP Tools", toolsPanel)
        
        // Query/Chat tab - interact with MCP server
        val queryPanel = createQueryPanel()
        tabbedPane.addTab("Query MCP", queryPanel)
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }
    
    private fun createToolsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // Split pane: tools list on left, tool details/execution on right
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        
        // Left: Tools list
        val toolsList = JList<String>()
        val toolsModel = DefaultListModel<String>()
        this.toolsModel = toolsModel // Store reference for refreshing
        toolsList.model = toolsModel
        toolsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        val toolsScrollPane = JBScrollPane(toolsList)
        toolsScrollPane.border = JBUI.Borders.empty(5)
        splitPane.leftComponent = toolsScrollPane
        
        // Right: Tool details and execution panel
        val toolDetailsPanel = createToolDetailsPanel(toolsList, toolsModel)
        splitPane.rightComponent = toolDetailsPanel
        
        splitPane.dividerLocation = 300
        splitPane.isOneTouchExpandable = true
        
        // Add refresh button
        val refreshToolsButton = JButton("Refresh Tools List")
        refreshToolsButton.addActionListener {
            loadAvailableToolsIntoList(toolsModel)
        }
        
        val buttonPanel = JPanel()
        buttonPanel.add(refreshToolsButton)
        panel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(splitPane, BorderLayout.CENTER)
        
        // Load tools on creation only if connected
        if (clientService.isConnected()) {
            loadAvailableToolsIntoList(toolsModel)
        } else {
            toolsModel.addElement("Not connected - connect first to see tools")
        }
        
        return panel
    }
    
    private fun createToolDetailsPanel(toolsList: JList<String>, toolsModel: DefaultListModel<String>): JPanel {
        val panel = JPanel(BorderLayout())
        val detailsArea = JTextArea()
        detailsArea.isEditable = false
        detailsArea.font = detailsArea.font.deriveFont(11f)
        detailsArea.text = "Select a tool from the list to see details and execute it."
        
        val detailsScrollPane = JBScrollPane(detailsArea)
        detailsScrollPane.border = JBUI.Borders.empty(5)
        panel.add(detailsScrollPane, BorderLayout.CENTER)
        
        // Execute button
        val executeButton = JButton("Execute Tool")
        executeButton.isEnabled = false
        var currentToolName = ""
        
        toolsList.addListSelectionListener {
            val selectedValue = toolsList.selectedValue as? String
            if (selectedValue != null) {
                val toolName = selectedValue.substringBefore(":")
                currentToolName = toolName
                val tool = toolDataMap[toolName]
                if (tool != null) {
                    val details = StringBuilder()
                    details.append("Tool: ${tool.name}\n\n")
                    details.append("Description: ${tool.description ?: "No description"}\n\n")
                    if (tool.arguments != null && tool.arguments.size() > 0) {
                        details.append("Parameters:\n")
                        val paramKeys = tool.arguments.keySet()
                        paramKeys.forEach { param: String ->
                            val paramObj = tool.arguments.get(param)?.asJsonObject
                            val paramType = paramObj?.get("type")?.asString ?: "unknown"
                            val paramDesc = paramObj?.get("description")?.asString
                            details.append("  - $param ($paramType)")
                            if (paramDesc != null) {
                                details.append(": $paramDesc")
                            }
                            details.append("\n")
                        }
                    } else {
                        details.append("No parameters required\n")
                    }
                    detailsArea.text = details.toString()
                    executeButton.isEnabled = true
                } else {
                    detailsArea.text = "Tool data not loaded. Try refreshing."
                    executeButton.isEnabled = false
                }
            } else {
                detailsArea.text = "Select a tool from the list to see details and execute it."
                executeButton.isEnabled = false
            }
        }
        
        executeButton.addActionListener {
            if (currentToolName.isNotEmpty()) {
                executeTool(currentToolName, toolDataMap[currentToolName], detailsArea)
            }
        }
        
        val buttonPanel = JPanel()
        buttonPanel.add(executeButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun executeTool(toolName: String, tool: McpTool?, detailsArea: JTextArea) {
        scope.launch {
            // Check connection first
            if (!clientService.isConnected()) {
                ApplicationManager.getApplication().invokeLater {
                    detailsArea.append("\n\n✗ Error: Not connected to MCP server. Please connect first.\n")
                    detailsArea.caretPosition = detailsArea.document.length
                }
                return@launch
            }
            
            ApplicationManager.getApplication().invokeLater {
                detailsArea.append("\n\n--- Executing $toolName ---\n")
            }
            
            val client = clientService.getClient()
            try {
                // For now, call with empty arguments - user can use Query tab for custom parameters
                val arguments = emptyMap<String, Any>()
                val response = client.callTool(toolName, arguments)
                
                ApplicationManager.getApplication().invokeLater {
                    if (response != null) {
                        if (response.has("result")) {
                            val result = response.getAsJsonObject("result")
                            val content = result.get("content")
                            if (content != null) {
                                val formattedJson = try {
                                    val jsonElement = gson.toJsonTree(content)
                                    com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
                                } catch (e: Exception) {
                                    gson.toJson(content)
                                }
                                detailsArea.append("\n✓ Success\nResult:\n$formattedJson\n")
                            } else {
                                val formattedJson = try {
                                    val jsonElement = gson.toJsonTree(result)
                                    com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
                                } catch (e: Exception) {
                                    gson.toJson(result)
                                }
                                detailsArea.append("\n✓ Success\nResult:\n$formattedJson\n")
                            }
                        } else if (response.has("error")) {
                            val error = response.getAsJsonObject("error")
                            val errorCode = error.get("code")?.asInt
                            val errorMessage = error.get("message")?.asString ?: "Unknown error"
                            detailsArea.append("\n✗ Error")
                            if (errorCode != null) {
                                detailsArea.append(" (code: $errorCode)")
                            }
                            detailsArea.append("\n$errorMessage\n")
                            if (error.has("data")) {
                                detailsArea.append("Details: ${gson.toJson(error.get("data"))}\n")
                            }
                        } else {
                            val formattedJson = try {
                                val jsonElement = gson.toJsonTree(response)
                                com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
                            } catch (e: Exception) {
                                gson.toJson(response)
                            }
                            detailsArea.append("\nResponse:\n$formattedJson\n")
                        }
                    } else {
                        detailsArea.append("\n✗ No response received\n")
                    }
                    detailsArea.caretPosition = detailsArea.document.length
                }
            } catch (e: Exception) {
                logger.error("Error executing tool $toolName", e)
                ApplicationManager.getApplication().invokeLater {
                    detailsArea.append("\n✗ Error executing tool: ${e.message}\n")
                    detailsArea.caretPosition = detailsArea.document.length
                }
            }
        }
    }
    
    private fun loadAvailableToolsIntoList(model: DefaultListModel<String>) {
        scope.launch {
            // Check connection first
            if (!clientService.isConnected()) {
                ApplicationManager.getApplication().invokeLater {
                    model.clear()
                    toolDataMap.clear()
                    model.addElement("Not connected - please connect first")
                }
                return@launch
            }
            
            val client = clientService.getClient()
            try {
                val tools = client.listTools()
                
                ApplicationManager.getApplication().invokeLater {
                    model.clear()
                    toolDataMap.clear()
                    if (tools.isEmpty()) {
                        model.addElement("No tools available")
                    } else {
                        tools.forEach { tool ->
                            val description = tool.description ?: "No description"
                            val shortDesc = if (description.length > 60) description.substring(0, 57) + "..." else description
                            model.addElement("${tool.name}: $shortDesc")
                            toolDataMap[tool.name] = tool
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error listing tools", e)
                ApplicationManager.getApplication().invokeLater {
                    model.clear()
                    toolDataMap.clear()
                    model.addElement("Error listing tools: ${e.message}")
                }
            }
        }
    }
    
    private fun createQueryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // Output area for responses
        val outputArea = JTextArea()
        outputArea.isEditable = false
        outputArea.font = outputArea.font.deriveFont(12f)
        outputArea.text = "Enter a query below and click 'Send' to interact with the Akeyless MCP server.\n\n" +
                "Examples:\n" +
                "- List all secrets\n" +
                "- List items in path /my/path\n" +
                "- Show details for /path/to/secret\n" +
                "- Create a new secret named 'my-secret'\n" +
                "- Get analytics data\n" +
                "- List all roles\n" +
                "- List auth methods\n" +
                "- List gateways\n\n" +
                "You can also use the 'MCP Tools' tab to see all available tools and execute them directly.\n"
        
        val outputScrollPane = JBScrollPane(outputArea)
        outputScrollPane.border = JBUI.Borders.empty(5)
        panel.add(outputScrollPane, BorderLayout.CENTER)
        
        // Input area and button
        val inputPanel = JPanel(BorderLayout())
        val inputField = JTextField()
        inputField.toolTipText = "Enter your query or command"
        
        val sendButton = JButton("Send")
        sendButton.addActionListener {
            val query = inputField.text.trim()
            if (query.isNotEmpty()) {
                outputArea.append("\n\n> $query\n")
                inputField.text = ""
                processQuery(query, outputArea)
            }
        }
        
        // Allow Enter key to send
        inputField.addActionListener {
            sendButton.doClick()
        }
        
        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(sendButton, BorderLayout.EAST)
        inputPanel.border = JBUI.Borders.empty(5)
        panel.add(inputPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun processQuery(query: String, outputArea: JTextArea) {
        scope.launch {
            // Check connection first
            if (!clientService.isConnected()) {
                ApplicationManager.getApplication().invokeLater {
                    outputArea.append("\n✗ Error: Not connected to MCP server. Please connect first.\n")
                    outputArea.caretPosition = outputArea.document.length
                }
                return@launch
            }
            
            ApplicationManager.getApplication().invokeLater {
                outputArea.append("Processing...\n")
            }
            
            val client = clientService.getClient()
            
            // Try to determine which tool to call based on query
            val toolName = determineToolFromQuery(query)
            val arguments = extractArgumentsFromQuery(query, toolName)
            
            ApplicationManager.getApplication().invokeLater {
                outputArea.append("Calling tool: $toolName\n")
                if (arguments.isNotEmpty()) {
                    outputArea.append("Arguments: $arguments\n")
                }
            }
            
            try {
                val response = withTimeoutOrNull(30_000) {
                    client.callTool(toolName, arguments)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (response != null) {
                        if (response.has("result")) {
                            val result = response.getAsJsonObject("result")
                            val content = result.get("content")
                            if (content != null) {
                                // Format JSON nicely
                                val formattedJson = try {
                                    val jsonElement = gson.toJsonTree(content)
                                    com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
                                } catch (e: Exception) {
                                    gson.toJson(content)
                                }
                                outputArea.append("\n✓ Success\nResponse:\n$formattedJson\n")
                            } else {
                                val formattedJson = try {
                                    val jsonElement = gson.toJsonTree(result)
                                    com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
                                } catch (e: Exception) {
                                    gson.toJson(result)
                                }
                                outputArea.append("\n✓ Success\nResponse:\n$formattedJson\n")
                            }
                        } else if (response.has("error")) {
                            val error = response.getAsJsonObject("error")
                            val errorCode = error.get("code")?.asInt
                            val errorMessage = error.get("message")?.asString ?: "Unknown error"
                            outputArea.append("\n✗ Error")
                            if (errorCode != null) {
                                outputArea.append(" (code: $errorCode)")
                            }
                            outputArea.append("\n$errorMessage\n")
                            if (error.has("data")) {
                                outputArea.append("Details: ${gson.toJson(error.get("data"))}\n")
                            }
                        } else {
                            val formattedJson = try {
                                val jsonElement = gson.toJsonTree(response)
                                com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement)
                            } catch (e: Exception) {
                                gson.toJson(response)
                            }
                            outputArea.append("\nResponse:\n$formattedJson\n")
                        }
                    } else {
                        outputArea.append("\n✗ No response received (timed out or connection lost). Try again.\n")
                    }
                    outputArea.caretPosition = outputArea.document.length
                }
            } catch (e: Exception) {
                logger.error("Error processing query: $query", e)
                ApplicationManager.getApplication().invokeLater {
                    outputArea.append("\n✗ Error: ${e.message}\n")
                    outputArea.caretPosition = outputArea.document.length
                }
            }
        }
    }
    
    private fun determineToolFromQuery(query: String): String {
        val lowerQuery = query.lowercase()
        return when {
            lowerQuery.contains("list") && (lowerQuery.contains("secret") || lowerQuery.contains("item")) -> "list_items"
            lowerQuery.contains("create") && lowerQuery.contains("secret") -> "create_secret"
            lowerQuery.contains("delete") -> "delete_item"
            lowerQuery.contains("describe") || lowerQuery.contains("details") || lowerQuery.contains("show") -> "describe_item"
            lowerQuery.contains("update") -> "update_item"
            lowerQuery.contains("analytics") -> "get_analytics_data"
            lowerQuery.contains("auth") && lowerQuery.contains("method") -> "get_auth_method"
            lowerQuery.contains("role") -> "get_role"
            lowerQuery.contains("tag") -> "get_tags"
            lowerQuery.contains("target") -> "target_get"
            lowerQuery.contains("gateway") -> "list_gateways"
            lowerQuery.contains("group") -> "list_groups"
            else -> "list_items" // Default fallback
        }
    }
    
    private fun extractArgumentsFromQuery(query: String, toolName: String): Map<String, Any> {
        val arguments = mutableMapOf<String, Any>()
        
        when (toolName) {
            "list_items" -> {
                // Try to extract path
                val pathMatch = Regex("(?:path|in|from)\\s+(/\\S+|['\"]([^'\"]+)['\"])", RegexOption.IGNORE_CASE).find(query)
                if (pathMatch != null) {
                    val path = pathMatch.groupValues[2].ifEmpty { pathMatch.groupValues[1] }
                    arguments["path"] = path
                } else {
                    arguments["path"] = "/"
                }
                
                // Try to extract type filter
                val typeMatch = Regex("(?:type|kind)\\s+(\\w+)", RegexOption.IGNORE_CASE).find(query)
                if (typeMatch != null) {
                    arguments["type"] = listOf(typeMatch.groupValues[1])
                }
                
                arguments["output_fields"] = listOf("ItemName", "ItemType", "ItemMetadata", "ItemTags")
            }
            "describe_item", "delete_item", "update_item" -> {
                // Try to extract item name/path
                val nameMatch = Regex("(?:name|item|secret)\\s+(/\\S+|['\"]([^'\"]+)['\"]|\\S+)", RegexOption.IGNORE_CASE).find(query)
                if (nameMatch != null) {
                    val name = nameMatch.groupValues[2].ifEmpty { nameMatch.groupValues[1] }
                    arguments["name"] = name
                }
            }
            "create_secret" -> {
                val nameMatch = Regex("(?:name|named)\\s+(['\"]([^'\"]+)['\"]|\\S+)", RegexOption.IGNORE_CASE).find(query)
                if (nameMatch != null) {
                    val name = nameMatch.groupValues[2].ifEmpty { nameMatch.groupValues[1] }
                    arguments["name"] = name
                }
                
                val valueMatch = Regex("(?:value|with|=)\\s+(['\"]([^'\"]+)['\"]|\\S+)", RegexOption.IGNORE_CASE).find(query)
                if (valueMatch != null) {
                    val value = valueMatch.groupValues[2].ifEmpty { valueMatch.groupValues[1] }
                    arguments["value"] = value
                }
            }
        }
        
        return arguments
    }
    
    fun getComponent(): JComponent = mainPanel
    
    fun checkConnection() {
        // Check connection status and update UI
        scope.launch {
            val isConnected = clientService.isConnected()
            ApplicationManager.getApplication().invokeLater {
                updateConnectionStatus(isConnected)
                if (!isConnected) {
                    connect()
                }
            }
        }
    }
    
    private fun loadAvailableTools() {
        scope.launch {
            val client = clientService.getClient()
            val tools = client.listTools()
            logger.info("Available MCP tools: ${tools.size}")
            tools.forEach { tool ->
                logger.info("Tool: ${tool.name} - ${tool.description}")
            }
        }
    }
    
    private fun connect() {
        val state = settings.state
        if (state.autoConnect || state.serverCommand.isNotEmpty()) {
            scope.launch {
                statusLabel.text = "Connecting..."
                statusLabel.icon = AllIcons.Actions.Refresh
                
                val serverArgs = if (state.serverArgs.isNotEmpty()) state.serverArgs else null
                val workingDir = if (state.workingDirectory.isNotEmpty()) state.workingDirectory else null
                
                // Long timeout: SAML auth may open browser and wait for user login
                kotlinx.coroutines.withTimeoutOrNull(150_000) { // 150 second timeout
                    clientService.connect(state.serverCommand, serverArgs, workingDir) { connected ->
                        ApplicationManager.getApplication().invokeLater {
                            if (connected) {
                                statusLabel.text = "Connected"
                                statusLabel.icon = AllIcons.General.InspectionsOK
                                refreshItems()
                                toolsModel?.let { loadAvailableToolsIntoList(it) }
                            } else {
                                // Check if actually connected (might be false negative)
                                if (clientService.isConnected()) {
                                    statusLabel.text = "Connected"
                                    statusLabel.icon = AllIcons.General.InspectionsOK
                                    refreshItems()
                                    toolsModel?.let { loadAvailableToolsIntoList(it) }
                                } else {
                                    val err = clientService.getLastConnectionError()
                                    statusLabel.text = if (!err.isNullOrBlank()) {
                                        "Connection failed: ${err.replace("\n", " ").take(120)}${if (err.length > 120) "…" else ""}"
                                    } else {
                                        "Connection failed - Check Settings & Logs"
                                    }
                                    statusLabel.icon = AllIcons.General.Error
                                }
                            }
                        }
                    }
                } ?: run {
                    // Timeout occurred
                    ApplicationManager.getApplication().invokeLater {
                        // Check if actually connected despite timeout
                        if (clientService.isConnected()) {
                            statusLabel.text = "Connected"
                            statusLabel.icon = AllIcons.General.InspectionsOK
                            refreshItems()
                            toolsModel?.let { loadAvailableToolsIntoList(it) }
                        } else {
                            statusLabel.text = "Connection timeout - Check Logs (Help > Show Log)"
                            statusLabel.icon = AllIcons.General.Error
                            logger.warn("Connection attempt timed out after 20 seconds")
                        }
                    }
                }
            }
        }
    }
    
    fun refreshItems() {
        scope.launch {
            ApplicationManager.getApplication().invokeLater {
                statusLabel.text = "Loading items..."
                statusLabel.icon = AllIcons.Actions.Refresh
            }
            
            val client = clientService.getClient()
            
            // Call list_items WITHOUT path to get ALL items recursively
            // (omitting path returns all items across all folders, like CLI "akeyless list-items --json")
            val response = withTimeoutOrNull(60_000) {
                client.callTool("list_items", mapOf(
                    "output_fields" to listOf("ItemName", "ItemType", "ItemSubType", "ItemMetadata", "ItemTags")
                ))
            }
            
            ApplicationManager.getApplication().invokeLater {
                try {
                    if (response != null && response.has("result")) {
                        val result = response.getAsJsonObject("result")
                        val content = extractContentArray(result)
                        
                        val root = DefaultMutableTreeNode("Akeyless Items")
                        treeModel.setRoot(root)
                        
                        if (content != null && content.size() > 0) {
                            logger.info("list_items returned ${content.size()} items total")
                            // Log a few samples
                            for (i in 0 until minOf(3, content.size())) {
                                if (content[i].isJsonObject) {
                                    val name = content[i].asJsonObject.get("ItemName")?.asString ?: "?"
                                    logger.info("  Sample: $name")
                                }
                            }
                            parseItems(content, root)
                        }
                        
                        if (root.childCount == 0) {
                            root.add(DefaultMutableTreeNode("(No items found)"))
                        }
                        
                        treeModel.reload()
                        statusLabel.text = "Connected (${content?.size() ?: 0} items)"
                        statusLabel.icon = AllIcons.General.InspectionsOK
                    } else {
                        setItemsTreePlaceholder(
                            if (response == null) "Load timed out — click Refresh"
                            else "No items — click Refresh"
                        )
                        statusLabel.text = if (response == null) "Timed out" else "No items"
                        statusLabel.icon = AllIcons.General.Warning
                    }
                } catch (e: Exception) {
                    logger.error("Error building items tree", e)
                    statusLabel.text = "Failed: ${e.message?.take(60) ?: "error"}"
                    statusLabel.icon = AllIcons.General.Error
                    setItemsTreePlaceholder("Error — click Refresh to try again")
                }
            }
        }
    }

    private fun setItemsTreePlaceholder(message: String) {
        val root = DefaultMutableTreeNode("Akeyless Items")
        root.add(DefaultMutableTreeNode(message))
        treeModel.setRoot(root)
        treeModel.reload()
    }

    /** Extract content as JsonArray; MCP may return content as array or as [{type, text}] with JSON in text. */
    private fun extractContentArray(result: JsonObject): JsonArray? {
        val contentEl = result.get("content") ?: return null
        if (contentEl.isJsonArray) {
            val arr = contentEl.asJsonArray
            // MCP format: [{ "type": "text", "text": "[{...}]" }] - parse first text as JSON array
            if (arr.size() > 0) {
                val first = arr.get(0)
                if (first.isJsonObject && first.asJsonObject.has("text")) {
                    return try {
                        JsonParser.parseString(first.asJsonObject.get("text").asString).asJsonArray
                    } catch (_: Exception) {
                        null
                    }
                }
                return arr
            }
            return arr
        }
        if (contentEl.isJsonObject && contentEl.asJsonObject.has("text")) {
            return try {
                JsonParser.parseString(contentEl.asJsonObject.get("text").asString).asJsonArray
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
    
    /**
     * Build a hierarchical folder tree from flat item paths.
     * E.g. "/folder/subfolder/secret" becomes folder > subfolder > secret in the tree.
     */
    private fun parseItems(itemsArray: JsonArray, rootNode: DefaultMutableTreeNode) {
        // Map of folder path -> tree node
        val folderNodes = mutableMapOf<String, DefaultMutableTreeNode>()
        
        // Collect all items
        val items = mutableListOf<Pair<String, AkeylessItem>>()
        for (item in itemsArray) {
            if (!item.isJsonObject) continue
            val itemObj = item.asJsonObject
            val itemName = itemObj.get("ItemName")?.asString
                ?: itemObj.get("item_name")?.asString
                ?: itemObj.get("name")?.asString
                ?: itemObj.get("Name")?.asString
                ?: "Unknown"
            val itemType = itemObj.get("ItemType")?.asString
                ?: itemObj.get("item_type")?.asString
                ?: itemObj.get("type")?.asString
                ?: itemObj.get("Type")?.asString
                ?: "Unknown"
            items.add(Pair(itemName, AkeylessItem(itemName, itemType, itemObj)))
        }
        
        // Sort items by path for consistent folder ordering
        items.sortBy { it.first }
        
        for ((fullPath, akeylessItem) in items) {
            // Split path: "/folder/subfolder/secret" -> ["folder", "subfolder", "secret"]
            val parts = fullPath.trimStart('/').split("/").filter { it.isNotEmpty() }
            
            if (parts.isEmpty()) continue
            
            if (parts.size == 1) {
                // Root-level item
                rootNode.add(DefaultMutableTreeNode(akeylessItem))
            } else {
                // Nested item: create folder hierarchy
                var currentParent = rootNode
                var currentPath = ""
                
                for (i in 0 until parts.size - 1) {
                    currentPath += "/${parts[i]}"
                    val existingFolder = folderNodes[currentPath]
                    if (existingFolder != null) {
                        currentParent = existingFolder
                    } else {
                        val folderNode = DefaultMutableTreeNode(FolderItem(parts[i], currentPath))
                        currentParent.add(folderNode)
                        folderNodes[currentPath] = folderNode
                        currentParent = folderNode
                    }
                }
                
                // Add the actual item as a leaf under the deepest folder
                // Use just the item's short name for display
                val displayItem = AkeylessItem(parts.last(), akeylessItem.type, akeylessItem.data)
                currentParent.add(DefaultMutableTreeNode(displayItem))
            }
        }
    }
    
    private fun showItemDetails(item: AkeylessItem) {
        // Show basic info immediately
        val fullName = item.data.get("ItemName")?.asString
            ?: item.data.get("item_name")?.asString
            ?: item.name
        
        val basicDetails = StringBuilder()
        basicDetails.append("Name: $fullName\n")
        basicDetails.append("Type: ${formatItemType(item.type)}\n\n")
        basicDetails.append("Loading full details...\n")
        detailsTextArea.text = basicDetails.toString()
        
        // Call describe_item for full details
        scope.launch {
            val client = clientService.getClient()
            val response = withTimeoutOrNull(15_000) {
                client.callTool("describe_item", mapOf("name" to fullName))
            }
            
            ApplicationManager.getApplication().invokeLater {
                val details = StringBuilder()
                details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                details.append("  ${formatItemType(item.type)}\n")
                details.append("  $fullName\n")
                details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
                
                if (response != null && response.has("result")) {
                    val result = response.getAsJsonObject("result")
                    val contentEl = result.get("content")
                    
                    // Parse the content (MCP format: [{type: "text", text: "..."}])
                    val detailJson = try {
                        if (contentEl != null && contentEl.isJsonArray) {
                            val arr = contentEl.asJsonArray
                            if (arr.size() > 0 && arr[0].isJsonObject && arr[0].asJsonObject.has("text")) {
                                JsonParser.parseString(arr[0].asJsonObject.get("text").asString).asJsonObject
                            } else null
                        } else null
                    } catch (_: Exception) { null }
                    
                    if (detailJson != null) {
                        // Show key fields in a nice format
                        appendField(details, "Item Name", detailJson, "ItemName", "item_name")
                        appendField(details, "Item Type", detailJson, "ItemType", "item_type")
                        appendField(details, "Item ID", detailJson, "ItemId", "item_id")
                        appendField(details, "Enabled", detailJson, "IsEnabled", "is_enabled")
                        appendField(details, "Description", detailJson, "ItemMetadata", "item_metadata")
                        appendField(details, "Tags", detailJson, "ItemTags", "item_tags")
                        appendField(details, "Created", detailJson, "CreationDate", "creation_date")
                        appendField(details, "Modified", detailJson, "ModificationDate", "modification_date")
                        appendField(details, "Last Accessed", detailJson, "AccessDate", "access_date")
                        appendField(details, "Last Version", detailJson, "LastVersion", "last_version")
                        appendField(details, "Item Size", detailJson, "ItemSize", "item_size")
                        appendField(details, "Delete Protection", detailJson, "DeleteProtection", "delete_protection")
                        appendField(details, "Protection Key", detailJson, "ProtectionKeyName", "protection_key_name")
                        appendField(details, "Permissions", detailJson, "ClientPermissions", "client_permissions")
                        
                        details.append("\n--- Full JSON ---\n")
                        details.append(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(detailJson))
                    } else {
                        // Fallback: show raw result
                        details.append(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result))
                    }
                } else {
                    // Fallback to basic data from list_items
                    details.append("(Could not load full details)\n\n")
                    item.data.entrySet().forEach { (key, value) ->
                        val valueStr = when {
                            value.isJsonNull -> "-"
                            value.isJsonPrimitive -> value.asJsonPrimitive.asString
                            value.isJsonArray -> value.asJsonArray.joinToString(", ") { it.asString }
                            else -> value.toString()
                        }
                        details.append("$key: $valueStr\n")
                    }
                }
                
                detailsTextArea.text = details.toString()
                detailsTextArea.caretPosition = 0
            }
        }
    }
    
    private fun appendField(sb: StringBuilder, label: String, json: JsonObject, vararg keys: String) {
        for (key in keys) {
            val el = json.get(key)
            if (el != null && !el.isJsonNull) {
                val value = when {
                    el.isJsonPrimitive -> el.asString
                    el.isJsonArray -> el.asJsonArray.joinToString(", ") { 
                        if (it.isJsonPrimitive) it.asString else it.toString() 
                    }
                    else -> el.toString()
                }
                if (value.isNotBlank() && value != "null" && value != "[]") {
                    sb.append("$label: $value\n")
                }
                return
            }
        }
    }
    
    private fun formatItemType(type: String): String {
        return type.replace("_", " ").split(" ").joinToString(" ") { 
            it.lowercase().replaceFirstChar { c -> c.uppercase() }
        }
    }
}

/**
 * Data class representing an Akeyless item
 */
data class AkeylessItem(
    val name: String,
    val type: String,
    val data: JsonObject
) {
    override fun toString(): String = name
}

/**
 * Represents a folder node in the tree (path segment, not a real Akeyless item)
 */
data class FolderItem(
    val name: String,
    val fullPath: String
) {
    override fun toString(): String = name
}

/**
 * Custom tree cell renderer for Akeyless items using the same icons
 * as the Akeyless Cursor Plugin (teal SVG icons).
 */
class AkeylessItemTreeCellRenderer : TreeCellRenderer {
    private val label = JBLabel()
    
    companion object {
        // Cache loaded icons
        private val iconCache = mutableMapOf<String, javax.swing.Icon>()
        
        private fun loadSvgIcon(resourcePath: String): javax.swing.Icon {
            return iconCache.getOrPut(resourcePath) {
                try {
                    com.intellij.openapi.util.IconLoader.getIcon(resourcePath, AkeylessItemTreeCellRenderer::class.java)
                } catch (_: Exception) {
                    AllIcons.Nodes.DataTables
                }
            }
        }
        
        private val staticSecretIcon by lazy { loadSvgIcon("/icons/static-secret.svg") }
        private val dynamicSecretIcon by lazy { loadSvgIcon("/icons/dynamic-secret.svg") }
        private val rotatedSecretIcon by lazy { loadSvgIcon("/icons/rotated-secret.svg") }
        private val folderIcon by lazy { loadSvgIcon("/icons/folder-icon.svg") }
        private val passwordIcon by lazy { loadSvgIcon("/icons/password-icon.svg") }
        private val dfcIcon by lazy { loadSvgIcon("/icons/dfc-icon.svg") }
    }
    
    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val node = value as? DefaultMutableTreeNode
        val userObj = node?.userObject
        
        when (userObj) {
            is AkeylessItem -> {
                label.text = userObj.name
                label.icon = getIconForType(userObj.type, userObj.data)
            }
            is FolderItem -> {
                label.text = userObj.name
                label.icon = folderIcon
            }
            is String -> {
                label.text = userObj
                label.icon = AllIcons.General.Information
            }
            else -> {
                label.text = value?.toString() ?: ""
                label.icon = null
            }
        }
        
        if (selected) {
            label.background = javax.swing.UIManager.getColor("List.selectionBackground")
            label.foreground = javax.swing.UIManager.getColor("List.selectionForeground")
        } else {
            label.background = null
            label.foreground = null
        }
        
        label.isOpaque = selected
        return label
    }
    
    private fun getIconForType(itemType: String, data: JsonObject? = null): javax.swing.Icon {
        // Check item_sub_type for password
        val subType = data?.get("ItemSubType")?.asString
            ?: data?.get("item_sub_type")?.asString
            ?: ""
        if (subType.lowercase() == "password") return passwordIcon
        
        return when (itemType.uppercase()) {
            "STATIC_SECRET" -> staticSecretIcon
            "DYNAMIC_SECRET" -> dynamicSecretIcon
            "ROTATED_SECRET" -> rotatedSecretIcon
            "CLASSIC_KEY", "RSA1024", "RSA2048", "RSA3072", "RSA4096",
            "AES128GCM", "AES256GCM", "AES128SIV", "AES256SIV",
            "AES256CBC" -> staticSecretIcon  // Key types use same style
            "USC" -> dfcIcon                  // Connector / DFC
            "CERTIFICATE", "PKI_CERT_ISSUER", "SSH_CERT_ISSUER" -> staticSecretIcon
            "VAULTLESS_TOK" -> dfcIcon        // Tokenizer
            else -> passwordIcon              // Default fallback
        }
    }
}
