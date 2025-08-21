package net.mcreator.MCreatorMCP.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mcreator.workspace.Workspace;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Core MCP Server implementation that handles the Model Context Protocol
 * communication according to the specification. This server runs directly
 * within the MCreator plugin without requiring external frameworks.
 */
public class McpServer {

    private static final Logger LOG = LogManager.getLogger("MCP-Server");

    private final ObjectMapper objectMapper;
    private final Map<String, McpHandler> handlers;
    private final AtomicLong requestIdCounter;
    private volatile boolean initialized = false;
    
    // Server information
    private final String serverName;
    private final String serverVersion;
    
    // MCreator integration
    private volatile Workspace currentWorkspace;
    
    // Capabilities
    private McpTypes.ServerCapabilities serverCapabilities;
    private McpTypes.ClientCapabilities clientCapabilities;

    public McpServer(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.objectMapper = new ObjectMapper();
        this.handlers = new ConcurrentHashMap<>();
        this.requestIdCounter = new AtomicLong(1);
        
        initializeCapabilities();
        registerDefaultHandlers();
    }

    /**
     * Initialize server capabilities according to MCP specification
     */
    private void initializeCapabilities() {
        serverCapabilities = new McpTypes.ServerCapabilities();
        
        // Resource capabilities
        McpTypes.ResourceCapabilities resourceCaps = new McpTypes.ResourceCapabilities();
        resourceCaps.setSubscribe(false); // Simple implementation without subscriptions for now
        resourceCaps.setListChanged(true);
        serverCapabilities.setResources(resourceCaps);
        
        // Tool capabilities
        McpTypes.ToolCapabilities toolCaps = new McpTypes.ToolCapabilities();
        toolCaps.setListChanged(true);
        serverCapabilities.setTools(toolCaps);
        
        LOG.info("MCP Server capabilities initialized");
    }

    /**
     * Register default MCP protocol handlers
     */
    private void registerDefaultHandlers() {
        // Core protocol handlers
        handlers.put("initialize", this::handleInitialize);
        handlers.put("initialized", this::handleInitialized);
        
        // Tool handlers
        handlers.put("tools/list", this::handleToolsList);
        handlers.put("tools/call", this::handleToolCall);
        
        // Resource handlers
        handlers.put("resources/list", this::handleResourcesList);
        handlers.put("resources/read", this::handleResourceRead);
        
        LOG.info("Default MCP handlers registered: {}", handlers.keySet());
    }

    /**
     * Process an incoming MCP message
     */
    public JsonRpcMessage processMessage(JsonRpcMessage message) {
        try {
            if (message.isRequest()) {
                return handleRequest(message);
            } else if (message.isNotification()) {
                handleNotification(message);
                return null; // Notifications don't return responses
            } else {
                LOG.warn("Received unexpected message type: {}", message);
                return createErrorResponse(message.getId(), -32600, "Invalid Request", 
                    "Expected request or notification");
            }
        } catch (Exception e) {
            LOG.error("Error processing MCP message", e);
            return createErrorResponse(message.getId(), -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Handle incoming requests
     */
    private JsonRpcMessage handleRequest(JsonRpcMessage message) {
        String method = message.getMethod();
        McpHandler handler = handlers.get(method);
        
        if (handler == null) {
            return createErrorResponse(message.getId(), -32601, "Method not found", 
                "Method '" + method + "' not supported");
        }

        try {
            Object result = handler.handle(message.getParams());
            return new JsonRpcMessage(message.getId(), result);
        } catch (Exception e) {
            LOG.error("Error handling method: " + method, e);
            return createErrorResponse(message.getId(), -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Handle incoming notifications
     */
    private void handleNotification(JsonRpcMessage message) {
        String method = message.getMethod();
        LOG.debug("Received notification: {}", method);
        
        // Handle notifications that don't require responses
        if ("initialized".equals(method)) {
            handleInitialized(message.getParams());
        }
    }

    /**
     * Handle initialize request
     */
    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        LOG.info("Handling MCP initialize request");
        
        // Extract client capabilities if provided
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) params.get("capabilities");
        if (capabilities != null) {
            try {
                clientCapabilities = objectMapper.convertValue(capabilities, McpTypes.ClientCapabilities.class);
                LOG.info("Client capabilities received: {}", capabilities);
            } catch (Exception e) {
                LOG.warn("Failed to parse client capabilities", e);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("protocolVersion", "2025-06-18");
        response.put("capabilities", serverCapabilities);
        
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);
        response.put("serverInfo", serverInfo);
        
        initialized = true;
        LOG.info("MCP server initialized successfully");
        
        return response;
    }

    /**
     * Handle initialized notification
     */
    private Object handleInitialized(Map<String, Object> params) {
        LOG.info("Received initialized notification from client");
        return null; // Notifications don't return values
    }

    /**
     * Handle tools/list request
     */
    private Map<String, Object> handleToolsList(Map<String, Object> params) {
        LOG.debug("Handling tools/list request");
        
        List<McpTypes.Tool> tools = new ArrayList<>();
        
        // Workspace management tools
        tools.add(createTool("buildWorkspace", "Build the current MCreator workspace", 
            Map.of("type", "object", "properties", Map.of())));
        
        tools.add(createTool("getWorkspaceInfo", "Get detailed workspace information",
            Map.of("type", "object", "properties", Map.of())));
        
        tools.add(createTool("regenerateCode", "Regenerate code without building",
            Map.of("type", "object", "properties", Map.of())));
        
        // Element operations
        tools.add(createTool("listModElements", "List mod elements with optional filtering",
            Map.of("type", "object", 
                   "properties", Map.of(
                       "elementType", Map.of("type", "string", "description", "Filter by element type")
                   ))));
        
        tools.add(createTool("createElement", "Create new mod element",
            Map.of("type", "object",
                   "properties", Map.of(
                       "elementType", Map.of("type", "string", "description", "Type of element to create"),
                       "elementName", Map.of("type", "string", "description", "Name of the new element")
                   ),
                   "required", List.of("elementType", "elementName"))));
        
        tools.add(createTool("deleteElement", "Delete mod element",
            Map.of("type", "object",
                   "properties", Map.of(
                       "elementName", Map.of("type", "string", "description", "Name of element to delete")
                   ),
                   "required", List.of("elementName"))));
        
        // Testing tools
        tools.add(createTool("runClient", "Start Minecraft client",
            Map.of("type", "object", "properties", Map.of())));
        
        tools.add(createTool("runServer", "Start Minecraft server",
            Map.of("type", "object", "properties", Map.of())));
        
        Map<String, Object> response = new HashMap<>();
        response.put("tools", tools);
        
        LOG.debug("Returning {} tools", tools.size());
        return response;
    }

    /**
     * Handle tools/call request
     */
    private Map<String, Object> handleToolCall(Map<String, Object> params) {
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        
        LOG.info("Handling tool call: {} with arguments: {}", toolName, arguments);
        
        try {
            McpTypes.ToolResult result = executeToolCall(toolName, arguments);
            Map<String, Object> response = new HashMap<>();
            response.put("content", result.getContent());
            response.put("isError", result.getIsError());
            return response;
        } catch (Exception e) {
            LOG.error("Error executing tool: " + toolName, e);
            List<McpTypes.ToolContent> errorContent = List.of(
                new McpTypes.ToolContent("text", "Error executing tool: " + e.getMessage())
            );
            Map<String, Object> response = new HashMap<>();
            response.put("content", errorContent);
            response.put("isError", true);
            return response;
        }
    }

    /**
     * Handle resources/list request
     */
    private Map<String, Object> handleResourcesList(Map<String, Object> params) {
        LOG.debug("Handling resources/list request");
        
        List<McpTypes.Resource> resources = new ArrayList<>();
        
        // Workspace overview resource
        McpTypes.Resource workspaceOverview = new McpTypes.Resource("workspace://overview", "Workspace Overview");
        workspaceOverview.setTitle("📁 Workspace Overview");
        workspaceOverview.setDescription("Complete overview of the MCreator workspace including metadata, settings, and statistics");
        workspaceOverview.setMimeType("application/json");
        resources.add(workspaceOverview);
        
        // Elements resource
        McpTypes.Resource elements = new McpTypes.Resource("workspace://elements", "Mod Elements");
        elements.setTitle("🧩 Mod Elements");
        elements.setDescription("All mod elements with properties and metadata");
        elements.setMimeType("application/json");
        resources.add(elements);
        
        // Project structure resource
        McpTypes.Resource structure = new McpTypes.Resource("workspace://structure", "Project Structure");
        structure.setTitle("📂 Project Structure");
        structure.setDescription("Project directory structure and file organization");
        structure.setMimeType("application/json");
        resources.add(structure);
        
        Map<String, Object> response = new HashMap<>();
        response.put("resources", resources);
        
        LOG.debug("Returning {} resources", resources.size());
        return response;
    }

    /**
     * Handle resources/read request
     */
    private Map<String, Object> handleResourceRead(Map<String, Object> params) {
        String uri = (String) params.get("uri");
        LOG.debug("Handling resources/read request for URI: {}", uri);
        
        try {
            McpTypes.ResourceContent content = readResourceContent(uri);
            Map<String, Object> response = new HashMap<>();
            response.put("contents", List.of(content));
            return response;
        } catch (Exception e) {
            LOG.error("Error reading resource: " + uri, e);
            throw new RuntimeException("Failed to read resource: " + e.getMessage());
        }
    }

    /**
     * Execute a tool call
     */
    private McpTypes.ToolResult executeToolCall(String toolName, Map<String, Object> arguments) {
        // This will be implemented by delegating to the IPC endpoint
        // For now, return a placeholder
        String resultText = "Tool '" + toolName + "' executed successfully";
        
        if (currentWorkspace == null) {
            resultText = "No workspace loaded. Please open a MCreator workspace first.";
        }
        
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", resultText)
        );
        
        return new McpTypes.ToolResult(content, false);
    }

    /**
     * Read resource content
     */
    private McpTypes.ResourceContent readResourceContent(String uri) {
        McpTypes.ResourceContent content = new McpTypes.ResourceContent();
        content.setUri(uri);
        content.setMimeType("application/json");
        
        if ("workspace://overview".equals(uri)) {
            content.setName("Workspace Overview");
            content.setTitle("📁 Workspace Overview");
            
            if (currentWorkspace != null) {
                Map<String, Object> overview = new HashMap<>();
                overview.put("name", currentWorkspace.getWorkspaceSettings().getModName());
                overview.put("version", currentWorkspace.getWorkspaceSettings().getVersion());
                overview.put("author", currentWorkspace.getWorkspaceSettings().getAuthor());
                overview.put("description", currentWorkspace.getWorkspaceSettings().getDescription());
                overview.put("elementCount", currentWorkspace.getModElements().size());
                
                try {
                    content.setText(objectMapper.writeValueAsString(overview));
                } catch (IOException e) {
                    content.setText("{\"error\":\"Failed to serialize workspace overview\"}");
                }
            } else {
                content.setText("{\"error\":\"No workspace loaded\"}");
            }
        } else {
            content.setText("{\"error\":\"Resource not found\"}");
        }
        
        return content;
    }

    /**
     * Helper method to create tool definitions
     */
    private McpTypes.Tool createTool(String name, String description, Map<String, Object> inputSchema) {
        return new McpTypes.Tool(name, description, inputSchema);
    }

    /**
     * Helper method to create error responses
     */
    private JsonRpcMessage createErrorResponse(Object id, int code, String message, Object data) {
        JsonRpcMessage.JsonRpcError error = new JsonRpcMessage.JsonRpcError(code, message, data);
        return new JsonRpcMessage(id, error);
    }

    /**
     * Set the current MCreator workspace
     */
    public void setWorkspace(Workspace workspace) {
        this.currentWorkspace = workspace;
        LOG.info("Workspace set: {}", workspace != null ? workspace.getWorkspaceSettings().getModName() : "null");
    }

    /**
     * Get current workspace
     */
    public Workspace getWorkspace() {
        return currentWorkspace;
    }

    /**
     * Check if server is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Register a custom handler
     */
    public void registerHandler(String method, McpHandler handler) {
        handlers.put(method, handler);
        LOG.debug("Registered handler for method: {}", method);
    }

    /**
     * Functional interface for MCP handlers
     */
    @FunctionalInterface
    public interface McpHandler {
        Object handle(Map<String, Object> params) throws Exception;
    }
}