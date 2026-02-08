package com.akeyless.mcp.actions

import com.akeyless.mcp.McpClientService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.intellij.openapi.application.ApplicationManager
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action to create a new secret in Akeyless
 */
class CreateSecretAction : AnAction("Create Secret", "Create a new secret in Akeyless", null), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val dialog = CreateSecretDialog(e.project)
        if (dialog.showAndGet()) {
            val name = dialog.getName()
            val value = dialog.getValue()
            val path = dialog.getPath()
            
            if (name.isNotEmpty() && value.isNotEmpty()) {
                val clientService = McpClientService.getInstance()
                val scope = CoroutineScope(Dispatchers.Default)
                
                scope.launch {
                    val client = clientService.getClient()
                    val response = client.callTool("create_secret", mapOf(
                        "name" to name,
                        "value" to value,
                        "path" to path
                    ))
                    
                    ApplicationManager.getApplication().invokeLater {
                        if (response != null && response.has("result")) {
                            // Success - refresh items list
                            // You can add a notification here
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for creating a new secret
 */
class CreateSecretDialog(project: com.intellij.openapi.project.Project?) : DialogWrapper(project) {
    private val nameField = JBTextField()
    private val valueField = JBTextField()
    private val pathField = JBTextField("/")
    
    init {
        title = "Create Secret"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField, 1, false)
            .addLabeledComponent("Value:", valueField, 1, false)
            .addLabeledComponent("Path:", pathField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    fun getName(): String = nameField.text.trim()
    fun getValue(): String = valueField.text.trim()
    fun getPath(): String = pathField.text.trim().ifEmpty { "/" }
}
