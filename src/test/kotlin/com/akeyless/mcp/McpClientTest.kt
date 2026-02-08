package com.akeyless.mcp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class McpClientTest {

    @Test
    fun `test connect and list tools`() = runTest {
        // Create a mock MCP server script
        val mockServerScript = File.createTempFile("mock_mcp", ".sh")
        mockServerScript.deleteOnExit()
        mockServerScript.setExecutable(true)
        
        // This script reads stdin line by line and responds to MCP requests
        mockServerScript.writeText("""
            #!/bin/sh
            while read line; do
                if echo "${'$'}line" | grep -q "initialize"; then
                    echo '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"mock","version":"1.0"}}}'
                elif echo "${'$'}line" | grep -q "notifications/initialized"; then
                    # No response needed for notification
                    :
                elif echo "${'$'}line" | grep -q "tools/list"; then
                    echo '{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"test_tool","description":"A test tool","inputSchema":{"type":"object","properties":{}}}]}}'
                elif echo "${'$'}line" | grep -q "tools/call"; then
                    echo '{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"tool output"}]}}'
                fi
            done
        """.trimIndent())

        val client = McpClient()
        
        // Connect to the mock server
        val connected = client.connect(mockServerScript.absolutePath)
        assertTrue("Client should connect successfully", connected)
        
        // List tools
        val tools = client.listTools()
        assertEquals("Should find 1 tool", 1, tools.size)
        assertEquals("Tool name should match", "test_tool", tools[0].name)
        
        // Call tool
        val response = client.callTool("test_tool", emptyMap())
        assertTrue("Response should have result", response?.has("result") == true)
        
        client.disconnect()
    }
}
