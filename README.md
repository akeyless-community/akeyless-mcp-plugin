# Akeyless MCP Plugin for JetBrains IDEs

This plugin connects JetBrains IDEs to Akeyless through the Model Context Protocol (MCP) server, allowing you to manage secrets, keys, certificates, and other Akeyless resources directly from your IDE.

## Features

- **Connect to Akeyless MCP Server**: Configure and connect to your Akeyless MCP server instance
- **Browse Items**: View all Akeyless items (secrets, keys, certificates) in a tree view
- **Item Details**: Click on any item to view its details and metadata
- **Create Secrets**: Create new secrets directly from the IDE
- **Refresh**: Refresh the items list to see the latest changes
- **Settings**: Configure MCP server command, working directory, and Akeyless profile

## Installation

### Building from Source

1. Clone this repository
2. Open the project in IntelliJ IDEA
3. Run the Gradle build using the wrapper (recommended):
   ```bash
   ./gradlew buildPlugin
   ```
   
   Or if you prefer using your system Gradle:
   ```bash
   gradle buildPlugin
   ```
   
   **Note**: The project includes a Gradle wrapper configured for Gradle 8.5, which is compatible with the IntelliJ plugin. If you encounter compatibility issues with Gradle 9.x, use the wrapper (`./gradlew`) instead.

4. Install the plugin:
   - Go to `File` > `Settings` > `Plugins`
   - Click the gear icon and select `Install Plugin from Disk...`
   - Select the generated plugin ZIP file from `build/distributions/`

### Development Setup

1. Ensure you have:
   - JDK 17 or higher
   - IntelliJ IDEA 2023.2 or higher
   - Gradle 7.5+

2. Run the plugin in a sandbox IDE:
   ```bash
   ./gradlew runIde
   ```

## Configuration

1. Go to `File` > `Settings` > `Tools` > `Akeyless MCP`
2. Configure the following:
   - **Server Command**: Command to start the Akeyless MCP server (e.g., `npx -y @akeyless/cli-mcp`)
   - **Working Directory**: Optional working directory for the MCP server process
   - **Akeyless Profile**: Optional Akeyless CLI profile name (uses default if empty)
   - **Auto-connect**: Automatically connect when IDE starts

## Usage

1. Open the **Akeyless MCP** tool window (View > Tool Windows > Akeyless MCP)
2. The plugin will attempt to connect automatically if auto-connect is enabled
3. Use the toolbar buttons to:
   - **Refresh**: Refresh the items list
   - **Create Secret**: Create a new secret
   - **Configure**: Open settings

## MCP Server Setup

The plugin connects to an Akeyless MCP server using stdio transport. The MCP server should be configured similarly to how it's set up in Cursor:

- The server command should start the MCP server process (e.g., `npx -y @akeyless/cli-mcp`)
- The server communicates via JSON-RPC over stdio
- Authentication is handled by the Akeyless CLI (using profiles or environment variables)

## Requirements

- JetBrains IDE (IntelliJ IDEA, PyCharm, WebStorm, etc.) 2023.2 or higher
- Akeyless CLI installed and configured
- Node.js (if using `npx` to run the MCP server)

## Troubleshooting

### Connection Issues

- Verify the MCP server command is correct
- Check that Akeyless CLI is installed and configured
- Ensure the Akeyless profile (if specified) exists and is valid
- Check the IDE logs: `Help` > `Show Log in Files`

### Items Not Loading

- Verify you have permissions to list items in Akeyless
- Check the MCP server is running correctly
- Try refreshing the items list manually

## Development

### Project Structure

```
akeyless-mcp-plugin/
├── src/main/kotlin/com/akeyless/mcp/
│   ├── McpClient.kt              # MCP client implementation
│   ├── McpClientService.kt       # Application service for MCP client
│   ├── actions/                  # Action classes
│   ├── settings/                 # Settings UI and persistence
│   └── ui/                       # UI components
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml            # Plugin descriptor
└── build.gradle.kts             # Build configuration
```

## License

This plugin is provided as-is for connecting to Akeyless MCP servers.

## Support

For issues related to:
- **Plugin**: Open an issue in this repository
- **Akeyless MCP Server**: Contact Akeyless support or check Akeyless documentation
- **Akeyless CLI**: See [Akeyless CLI documentation](https://docs.akeyless.io/docs/cli)
