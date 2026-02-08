package com.akeyless.mcp.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware

/**
 * Action to open Akeyless MCP settings
 */
class ConfigureAction : AnAction("Configure", "Configure Akeyless MCP connection", AllIcons.General.Settings), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Akeyless MCP")
    }
}
