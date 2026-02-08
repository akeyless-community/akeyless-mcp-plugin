package com.akeyless.mcp.ui

import com.akeyless.mcp.actions.ConfigureAction
import com.akeyless.mcp.actions.CreateSecretAction
import com.akeyless.mcp.actions.RefreshItemsAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Akeyless MCP tool window
 */
class AkeylessMcpToolWindowFactory : ToolWindowFactory {
    private var panel: AkeylessMcpToolWindowPanel? = null
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        panel = AkeylessMcpToolWindowPanel(project)
        
        // Add toolbar actions
        val actionManager = ActionManager.getInstance()
        val actionGroup = DefaultActionGroup()
        actionGroup.add(actionManager.getAction("com.akeyless.mcp.actions.RefreshItemsAction"))
        actionGroup.add(actionManager.getAction("com.akeyless.mcp.actions.CreateSecretAction"))
        actionGroup.add(actionManager.getAction("com.akeyless.mcp.actions.ConfigureAction"))
        
        val toolbar = actionManager.createActionToolbar("AkeylessMcpToolbar", actionGroup, true)
        toolbar.targetComponent = panel!!.getComponent()
        
        val contentPanel = panel!!.getComponent()
        val mainPanel = javax.swing.JPanel(java.awt.BorderLayout())
        mainPanel.add(toolbar.component, java.awt.BorderLayout.NORTH)
        mainPanel.add(contentPanel, java.awt.BorderLayout.CENTER)
        
        val contentFactory = ContentFactory.getInstance()
        val toolWindowContent = contentFactory.createContent(mainPanel, "", false)
        // Store panel reference in content for actions to access
        val panelKey = com.intellij.openapi.util.Key.create<AkeylessMcpToolWindowPanel>("AkeylessMcpPanel")
        toolWindowContent.putUserData(panelKey, panel)
        toolWindow.contentManager.addContent(toolWindowContent)
    }
    
    fun getPanel(): AkeylessMcpToolWindowPanel? = panel
}
