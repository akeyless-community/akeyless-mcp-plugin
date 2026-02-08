package com.akeyless.mcp.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

/**
 * Persistent settings for Akeyless MCP configuration
 */
@State(name = "AkeylessMcpSettings", storages = [Storage("akeyless-mcp.xml")])
@Service
class AkeylessMcpSettings : PersistentStateComponent<AkeylessMcpSettings.State> {
    
    data class State(
        var serverCommand: String = "akeyless",
        var serverArgs: String = "mcp --gateway-url https://api.akeyless.io",
        var workingDirectory: String = "",
        var autoConnect: Boolean = false
    )
    
    private var state = State()
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    companion object {
        fun getInstance(): AkeylessMcpSettings {
            return ApplicationManager.getApplication().getService(AkeylessMcpSettings::class.java)
        }
    }
}
