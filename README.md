# MCreator MCP Integration Plugin

A lightweight Model Context Protocol (MCP) server implementation for MCreator that enables LLM applications to interact with MCreator workspaces, build projects, manage mod elements, and access workspace resources.

## Features

### 🛠️ **Direct Integration**
- Native MCP server implementation without external dependencies
- Direct MCreator API integration for optimal performance
- JSON-RPC 2.0 protocol compliance according to MCP specification
- No separate JAR processes or IPC overhead

### 🔧 **Core Tools**
- **Workspace Management**: Build, regenerate code, get info, settings
- **Element Operations**: List, create, edit, delete mod elements
- **Testing**: Run Minecraft client/server with mods
- **Resources**: Access workspace overview, elements, project structure

### 🌐 **Multiple Transport Support**
- **HTTP**: `http://localhost:<port>/mcp` (standard MCP protocol)
- **SSE**: `http://localhost:<port>/mcp/sse` (legacy compatibility)
- **Stdio**: Traditional MCP client support
- **Health**: `http://localhost:<port>/health` (monitoring)

### 📊 **Rich Resources**
- Complete workspace overview with metadata and statistics
- Project structure and file organization
- Live workspace statistics and element information
- Configuration and build information

## Quick Start

### Prerequisites
- Java 21+
- MCreator 2025.2+
- Git (for development)

### Installation

1. **Download/Build the Plugin**:
   ```bash
   git clone <repository-url>
   cd MCreatorMCP1
   ./gradlew jar
   ```

2. **Install Plugin**:
   - Copy `build/libs/MCreatorMCP.zip` to your MCreator plugins folder
   - Or use `./gradlew runMCreatorWithPlugin` for development

3. **Enable Java Plugins** in MCreator preferences

4. **Start MCreator** - the MCP server will start automatically

### Connecting LLM Clients

The MCP server exposes endpoints at (default port 5175 unless dynamically selected):
- **HTTP**: `http://localhost:<port>/mcp` (standard MCP protocol)
- **SSE**: `http://localhost:<port>/mcp/sse` (legacy compatibility)
- **Stdio**: Connect directly via stdin/stdout for traditional MCP clients
- **Health Check**: `http://localhost:<port>/health`

Configure your MCP-compatible client to connect to one of these endpoints.

## Architecture

### Simplified Direct Integration
```
┌─────────────────┐     Direct      ┌─────────────────┐
│   MCreator      │◄───────────────►│   LLM Client    │
│   + MCP Plugin  │    MCP Protocol │   (Editor/Agent)│
└─────────────────┘                 └─────────────────┘
```

- **MCreator Plugin**: Contains integrated MCP server with direct API access
- **No External Processes**: Everything runs within the plugin JVM
- **Direct Integration**: No IPC overhead, immediate MCreator API access
- **Clean Architecture**: Lightweight, fast, and maintainable

Note: Ports are dynamically selected to avoid conflicts. Check the plugin status dialog or logs for the actual port.

### Project Structure
```
MCreatorMCP/
├── src/main/java/net/mcreator/MCreatorMCP/
│   ├── MCreatorMCP.java              # Main plugin entry point
│   ├── MCPToolsService.java          # MCreator tool implementations
│   └── mcp/                          # MCP server implementation
│       ├── McpServer.java            # Core MCP server
│       ├── JsonRpcMessage.java       # JSON-RPC message handling
│       ├── McpTypes.java             # MCP protocol data types
│       ├── McpHttpTransport.java     # HTTP/SSE transport
│       └── McpStdioTransport.java    # Stdio transport
├── build.gradle                     # Plugin build configuration
└── settings.gradle                  # Project setup
```

## Available Tools

### Workspace Management
- `buildWorkspace()` - Build the current workspace
- `getWorkspaceInfo()` - Get detailed workspace information
- `regenerateCode()` - Regenerate code without building

### Element Operations
- `listModElements(elementType?)` - List mod elements with optional filtering
- `openElement(elementName)` - Open element in MCreator UI
- `createElement(elementType, elementName)` - Create new mod element
- `deleteElement(elementName)` - Delete mod element

### Testing & Execution
- `runClient()` - Start Minecraft client
- `runServer()` - Start Minecraft server

### Resource Management
- `listTextures()` - List texture files
- `listSounds()` - List sound files
- `listStructures()` - List structure files

### Variables & Localization
- `listVariables()` - List workspace variables
- `createVariable(name, type, scope)` - Create new variable
- `getLocalizations(language?)` - Get localization entries

## Resources

### Available Resources
- `workspace://overview` - Complete workspace overview with metadata
- `workspace://elements` - All mod elements with properties and details
- `workspace://structure` - Project directory structure and organization

## Configuration

The MCP server is automatically configured by the plugin with sensible defaults:

- **HTTP Port**: Auto-selected starting from 5175
- **Transport Methods**: HTTP, SSE, and Stdio enabled by default
- **Workspace Integration**: Automatic detection when MCreator workspace loads
- **Tool Registration**: All MCreator tools automatically registered

No additional configuration needed - just install and run!

## Development

### Building
```bash
# Build the plugin
./gradlew jar

# Run MCreator with plugin
./gradlew runMCreatorWithPlugin

# Clean build
./gradlew clean build
```

### Debugging
- Plugin logs: MCreator console output
- MCP Protocol: Enable DEBUG logging in MCreator console
- Network traffic: Monitor HTTP endpoints with browser dev tools

### Testing Tools
```bash
# Test MCP endpoints
curl http://localhost:<port>/health
curl -X POST http://localhost:<port>/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}'
```

## Troubleshooting

### Common Issues

**MCP Server won't start**:
- Check Java 21+ is available
- Verify the HTTP port is free (default 5175). The plugin will auto-select a free port.
- Check MCreator console for errors

**Tools not working**:
- Ensure MCreator workspace is loaded
- Check plugin status via "MCP Server Status" menu
- Restart MCP server if needed

**Client connection issues**:
- Try different transport methods (HTTP vs SSE vs Stdio)
- Check CORS settings for web clients
- Verify MCP client protocol version compatibility

### Monitoring
- Health checks: `http://localhost:<port>/health`
- Plugin status: "MCP Server Status" menu in MCreator
- Console logs: Enable DEBUG logging in MCreator

## Contributing

1. Fork the repository
2. Create a feature branch
3. Implement changes with tests
4. Update documentation
5. Submit a pull request

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Links

- [MCreator](https://mcreator.net/) - Minecraft mod creation platform
- [Model Context Protocol](https://modelcontextprotocol.io/) - Protocol specification
- [MCreator Plugin Development](https://mcreator.net/wiki/developing-mcreator-plugins) - Plugin development guide