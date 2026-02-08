package com.akeyless.mcp.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings panel UI for Akeyless MCP configuration
 */
class AkeylessMcpSettingsPanel {
    private val serverCommandField = JBTextField()
    private val serverArgsField = JBTextField()
    private val workingDirectoryField = JBTextField()
    private val autoConnectCheckBox = JBCheckBox("Auto-connect on IDE startup")
    
    init {
        serverCommandField.toolTipText = "Command to start the Akeyless MCP server (e.g., 'akeyless')"
        serverArgsField.toolTipText = "Arguments for the command (e.g., 'mcp --gateway-url https://api.akeyless.io --profile saml')"
        workingDirectoryField.toolTipText = "Working directory for the MCP server process (optional)"
        autoConnectCheckBox.toolTipText = "Automatically connect to Akeyless MCP when IDE starts"
    }
    
    fun getPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addComponent(JBLabel("MCP Server Configuration"))
            .addLabeledComponent("Server Command:", serverCommandField, 1, false)
            .addLabeledComponent("Command Arguments:", serverArgsField, 1, false)
            .addLabeledComponent("Working Directory:", workingDirectoryField, 1, false)
            .addComponent(autoConnectCheckBox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    fun getServerCommand(): String = serverCommandField.text.trim()
    fun setServerCommand(value: String) { serverCommandField.text = value }
    
    fun getServerArgs(): String = serverArgsField.text.trim()
    fun setServerArgs(value: String) { serverArgsField.text = value }
    
    fun getWorkingDirectory(): String = workingDirectoryField.text.trim()
    fun setWorkingDirectory(value: String) { workingDirectoryField.text = value }
    
    fun getAutoConnect(): Boolean = autoConnectCheckBox.isSelected
    fun setAutoConnect(value: Boolean) { autoConnectCheckBox.isSelected = value }
}
