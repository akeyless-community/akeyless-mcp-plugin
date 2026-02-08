package com.akeyless.mcp.actions

import com.akeyless.mcp.ui.AkeylessMcpToolWindowPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to refresh the Akeyless items list
 */
class RefreshItemsAction : AnAction("Refresh", "Refresh Akeyless items list", AllIcons.Actions.Refresh), DumbAware {
    companion object {
        private val PANEL_KEY = Key.create<AkeylessMcpToolWindowPanel>("AkeylessMcpPanel")
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Akeyless MCP") ?: return
        
        // Get the panel from content user data
        val panel = toolWindow.contentManager.contents.firstOrNull()?.getUserData(PANEL_KEY)
        panel?.refreshItems()
    }
}
