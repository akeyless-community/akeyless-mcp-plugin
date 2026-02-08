package com.akeyless.mcp.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.akeyless.mcp.ui.AkeylessMcpSettingsPanel
import javax.swing.JComponent

/**
 * Settings UI for Akeyless MCP configuration
 */
class AkeylessMcpConfigurable : Configurable {
    private val settings = AkeylessMcpSettings.getInstance()
    private val panel = AkeylessMcpSettingsPanel()
    
    override fun getDisplayName(): String = "Akeyless MCP"
    
    override fun createComponent(): JComponent? = panel.getPanel()
    
    override fun isModified(): Boolean {
        val state = settings.state
        return panel.getServerCommand() != state.serverCommand ||
               panel.getServerArgs() != state.serverArgs ||
               panel.getWorkingDirectory() != state.workingDirectory ||
               panel.getAutoConnect() != state.autoConnect
    }
    
    @Throws(ConfigurationException::class)
    override fun apply() {
        val state = settings.state
        state.serverCommand = panel.getServerCommand()
        state.serverArgs = panel.getServerArgs()
        state.workingDirectory = panel.getWorkingDirectory()
        state.autoConnect = panel.getAutoConnect()
    }
    
    override fun reset() {
        val state = settings.state
        panel.setServerCommand(state.serverCommand)
        panel.setServerArgs(state.serverArgs)
        panel.setWorkingDirectory(state.workingDirectory)
        panel.setAutoConnect(state.autoConnect)
    }
}
