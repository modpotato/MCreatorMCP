# MCreator MCP Integration Plugin

A comprehensive Model Context Protocol (MCP) server implementation for MCreator that enables LLM applications to interact with MCreator workspaces, build projects, manage mod elements, and access workspace resources.

## Features

### 🛠️ **Dynamic Tool Discovery**
- Automatically discovers and exposes MCreator APIs as MCP tools
- Built-in tools for workspace management, element creation, building, and testing
- Dynamically generated tools based on MCreator's Java SDK

### 🔧 **Core Tools**
- **Workspace Management**: Build, regenerate code, get info, settings
- **Element Operations**: List, create, edit, delete mod elements
- **Testing**: Run Minecraft client/server with mods
- **Resources**: Access textures, sounds, models, structures
- **Variables**: Manage workspace variables and localization
- **Project Structure**: Browse and analyze project files

### 🌐 **Multiple Transport Support**
- **Streamable HTTP** (MCP 2025-03-26): `http://localhost:<port>/mcp` (default 5175)
- **HTTP/SSE** (MCP 2024-11-05): `http://localhost:<port>/mcp/sse` (default 5175)
- Both transports supported simultaneously for maximum compatibility

### 📊 **Rich Resources**
- Complete workspace overview with metadata and statistics
- Project structure and file organization
- Live workspace statistics via IPC bridge
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
- **Streamable HTTP**: `http://localhost:<port>/mcp`
- **Legacy SSE**: `http://localhost:<port>/mcp/sse`
- **Health Check**: `http://localhost:<port>/health`
- **Admin Interface**: `http://localhost:<port>/admin/status`

Configure your MCP-compatible client to connect to one of these endpoints.

## Architecture

### Sidecar Process Design
```
┌─────────────────┐     IPC      ┌─────────────────┐     MCP     ┌─────────────────┐
│   MCreator      │◄────────────►│   MCP Server    │◄───────────►│   LLM Client    │
│   + Plugin      │   HTTP:9876  │   (Quarkus)     │  HTTP:<port>│   (Editor/Agent)│
└─────────────────┘              └─────────────────┘             └─────────────────┘
```

- **MCreator Plugin**: Manages MCP server lifecycle and provides IPC endpoint
- **MCP Server**: Standalone Quarkus application with dynamic tool discovery
- **IPC Bridge**: HTTP-based communication between plugin and server
- **Clean Separation**: No classpath conflicts, easy upgrades, stable operation

Note: Ports shown are defaults. The plugin may select a free port at runtime to avoid conflicts; check the plugin status dialog or logs for the actual port.

### Project Structure
```
MCreatorMCP1/
├── src/main/java/net/mcreator/MCreatorMCP/
│   ├── MCreatorMCP.java           # Main plugin entry point
│   └── MCPIPCEndpoint.java           # IPC server running in MCreator
├── mcp-server/                       # MCP Server (Quarkus)
│   ├── src/main/java/net/mcreator/mcp/
│   │   ├── MCPServerApplication.java # Main server application
│   │   ├── ToolDiscoveryService.java # Dynamic API discovery
│   │   ├── MCPToolsService.java      # Tool implementations
│   │   ├── WorkspaceResourceService.java # Resource providers
│   │   ├── IPCBridgeService.java     # IPC client
│   │   └── *.java                    # Supporting classes
│   └── src/main/resources/
│       └── application.properties    # Server configuration
├── build.gradle                     # Root build with plugin packaging
├── mcp-server/build.gradle          # MCP server build (Quarkus)
└── settings.gradle                  # Multi-project setup
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
- `workspace://overview` - Complete workspace overview
- `workspace://elements` - All mod elements with properties
- `workspace://variables` - Workspace variables
- `workspace://structure` - Project directory structure
- `workspace://resources` - Resource files (textures, sounds, etc.)
- `workspace://settings` - Workspace configuration
- `workspace://build-info` - Build and Gradle information

## Configuration

### MCP Server (application.properties)
```properties
# HTTP Configuration
quarkus.http.port=5175
quarkus.mcp.server.sse.root-path=/mcp

# MCreator Integration
mcreator.workspace.path=/path/to/workspace
mcreator.ipc.port=9876
mcreator.ipc.enabled=true
mcreator.tools.auto-discovery=true
```

### Environment Variables
- `MCREATOR_WORKSPACE` - Workspace path
- `MCREATOR_IPC_PORT` - IPC communication port
- `MCP_SERVER_PORT` - MCP server HTTP port

## Development

### Building
```bash
# Build everything
./gradlew build

# Build just the plugin
./gradlew jar

# Build just the MCP server
./gradlew :mcp-server:build

# Run MCreator with plugin
./gradlew runMCreatorWithPlugin

# Run MCP server in dev mode (for testing)
cd mcp-server && ../gradlew quarkusDev
```

### Debugging
- Plugin logs: MCreator console
- MCP Server logs: `{plugin-dir}/logs/mcp-server.log`
- IPC communication: Enable DEBUG logging in both components

### Testing Tools
```bash
# Test MCP endpoints
curl http://localhost:<port>/health
curl http://localhost:<port>/admin/status
curl http://localhost:<port>/admin/tools

# Test IPC
curl -X POST http://localhost:9876/mcp-ipc \
  -H "Content-Type: application/json" \
  -d '{"action":"ping"}'
```

## Troubleshooting

### Common Issues

**MCP Server won't start**:
- Check Java 21+ is available
- Verify the HTTP port is free (default 5175). The plugin can auto-select a free port to avoid conflicts.
- Check plugin logs for errors

**IPC communication fails**:
- Verify port 9876 is available
- Check MCreator workspace is loaded
- Restart both plugin and server

**Tools not discovered**:
- Enable `mcreator.tools.auto-discovery=true`
- Check MCreator classpath availability
- Review server startup logs

**Client connection issues**:
- Try legacy SSE endpoint if Streamable HTTP fails
- Check CORS settings for web clients
- Verify MCP client protocol version

### Monitoring
- Health checks: `http://localhost:<port>/health`
- Metrics: `http://localhost:<port>/admin/metrics`
- Tool registry: `http://localhost:<port>/admin/tools`

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
- [Quarkus MCP Server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/index.html) - MCP server framework
- [MCreator Plugin Development](https://mcreator.net/wiki/developing-mcreator-plugins) - Plugin development guide