package net.mcreator.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkiverse.mcp.server.Tool;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core MCP Tools Service that implements all the MCreator tools exposed via MCP.
 * This service provides the actual implementation for tools discovered by the
 * ToolDiscoveryService and handles communication with MCreator via IPC.
 */
@ApplicationScoped
@RegisterForReflection
public class MCPToolsService {

    private static final Logger LOG = Logger.getLogger(MCPToolsService.class);

    @ConfigProperty(name = "mcreator.workspace.path", defaultValue = "")
    String workspacePath;

    @Inject
    IPCBridgeService ipcBridge;

    @Inject
    MCPToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===============================
    // WORKSPACE MANAGEMENT TOOLS
    // ===============================

    @Tool(name = "buildWorkspace", description = "Build the current MCreator workspace")
    public String buildWorkspace() {
        LOG.info("Building MCreator workspace...");
        
        try {
            if (workspacePath.isEmpty()) {
                return createErrorResponse("No workspace path configured");
            }

            // Send build command via IPC
            Map<String, Object> command = Map.of(
                "action", "buildWorkspace",
                "timestamp", System.currentTimeMillis()
            );

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Build failed: " + response.get("error"));
            }

            return createSuccessResponse("Workspace build initiated successfully", 
                    Map.of("buildId", response.getOrDefault("buildId", "unknown")));

        } catch (Exception e) {
            LOG.error("Failed to build workspace", e);
            return createErrorResponse("Build failed: " + e.getMessage());
        }
    }

    @Tool(name = "getWorkspaceInfo", description = "Get detailed information about the current workspace")
    public String getWorkspaceInfo() {
        LOG.info("Getting workspace information...");
        
        try {
            if (workspacePath.isEmpty()) {
                return createErrorResponse("No workspace path configured");
            }

            Path workspaceDir = Paths.get(workspacePath);
            if (!Files.exists(workspaceDir)) {
                return createErrorResponse("Workspace directory does not exist: " + workspacePath);
            }

            Map<String, Object> workspaceInfo = new HashMap<>();
            workspaceInfo.put("path", workspacePath);
            workspaceInfo.put("exists", true);
            workspaceInfo.put("lastModified", Files.getLastModifiedTime(workspaceDir).toMillis());

            // Try to read workspace metadata
            Path metadataFile = workspaceDir.resolve(".mcreator").resolve("workspace.mcmeta");
            if (Files.exists(metadataFile)) {
                try {
                    String content = Files.readString(metadataFile);
                    JsonNode metadata = objectMapper.readTree(content);
                    workspaceInfo.put("metadata", metadata);
                } catch (Exception e) {
                    LOG.warn("Failed to read workspace metadata", e);
                }
            }

            // Get workspace statistics via IPC
            Map<String, Object> command = Map.of("action", "getWorkspaceStats");
            Map<String, Object> stats = ipcBridge.sendCommand(command);
            workspaceInfo.put("stats", stats);

            return createSuccessResponse("Workspace information retrieved", workspaceInfo);

        } catch (Exception e) {
            LOG.error("Failed to get workspace info", e);
            return createErrorResponse("Failed to get workspace info: " + e.getMessage());
        }
    }

    @Tool(name = "regenerateCode", description = "Regenerate workspace code without building")
    public String regenerateCode() {
        LOG.info("Regenerating workspace code...");
        
        try {
            Map<String, Object> command = Map.of("action", "regenerateCode");
            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Code regeneration failed: " + response.get("error"));
            }

            return createSuccessResponse("Code regeneration completed", response);

        } catch (Exception e) {
            LOG.error("Failed to regenerate code", e);
            return createErrorResponse("Code regeneration failed: " + e.getMessage());
        }
    }

    // ===============================
    // MOD ELEMENT MANAGEMENT TOOLS
    // ===============================

    @Tool(name = "listModElements", description = "List all mod elements in the workspace")
    public String listModElements(String elementType) {
        LOG.info("Listing mod elements (type filter: " + elementType + ")");
        
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("action", "listModElements");
            if (elementType != null && !elementType.trim().isEmpty()) {
                command.put("elementType", elementType.trim());
            }

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to list elements: " + response.get("error"));
            }

            return createSuccessResponse("Mod elements retrieved", response);

        } catch (Exception e) {
            LOG.error("Failed to list mod elements", e);
            return createErrorResponse("Failed to list mod elements: " + e.getMessage());
        }
    }

    @Tool(name = "openElement", description = "Open a mod element in the MCreator interface")
    public String openElement(String elementName) {
        LOG.info("Opening mod element: " + elementName);
        
        if (elementName == null || elementName.trim().isEmpty()) {
            return createErrorResponse("Element name is required");
        }

        try {
            Map<String, Object> command = Map.of(
                "action", "openElement",
                "elementName", elementName.trim()
            );

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to open element: " + response.get("error"));
            }

            return createSuccessResponse("Element opened successfully", 
                    Map.of("elementName", elementName));

        } catch (Exception e) {
            LOG.error("Failed to open element: {}", elementName, e);
            return createErrorResponse("Failed to open element: " + e.getMessage());
        }
    }

    @Tool(name = "createElement", description = "Create a new mod element")
    public String createElement(String elementType, String elementName) {
        LOG.info("Creating mod element: " + elementName + " of type " + elementType);
        
        if (elementType == null || elementType.trim().isEmpty()) {
            return createErrorResponse("Element type is required");
        }
        
        if (elementName == null || elementName.trim().isEmpty()) {
            return createErrorResponse("Element name is required");
        }

        try {
            Map<String, Object> command = Map.of(
                "action", "createElement",
                "elementType", elementType.trim(),
                "elementName", elementName.trim()
            );

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to create element: " + response.get("error"));
            }

            return createSuccessResponse("Element created successfully", response);

        } catch (Exception e) {
            LOG.error("Failed to create element: " + elementName + " (" + elementType + ")", e);
            return createErrorResponse("Failed to create element: " + e.getMessage());
        }
    }

    @Tool(name = "deleteElement", description = "Delete a mod element from the workspace")
    public String deleteElement(String elementName) {
        LOG.info("Deleting mod element: " + elementName);
        
        if (elementName == null || elementName.trim().isEmpty()) {
            return createErrorResponse("Element name is required");
        }

        try {
            Map<String, Object> command = Map.of(
                "action", "deleteElement",
                "elementName", elementName.trim()
            );

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to delete element: " + response.get("error"));
            }

            return createSuccessResponse("Element deleted successfully", 
                    Map.of("elementName", elementName));

        } catch (Exception e) {
            LOG.error("Failed to delete element: {}", elementName, e);
            return createErrorResponse("Failed to delete element: " + e.getMessage());
        }
    }

    // ===============================
    // TESTING AND EXECUTION TOOLS
    // ===============================

    @Tool(name = "runClient", description = "Start the Minecraft client with the mod loaded")
    public String runClient() {
        LOG.info("Starting Minecraft client...");
        
        try {
            Map<String, Object> command = Map.of("action", "runClient");
            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to start client: " + response.get("error"));
            }

            return createSuccessResponse("Minecraft client started", response);

        } catch (Exception e) {
            LOG.error("Failed to start client", e);
            return createErrorResponse("Failed to start client: " + e.getMessage());
        }
    }

    @Tool(name = "runServer", description = "Start the Minecraft server with the mod loaded")
    public String runServer() {
        LOG.info("Starting Minecraft server...");
        
        try {
            Map<String, Object> command = Map.of("action", "runServer");
            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to start server: " + response.get("error"));
            }

            return createSuccessResponse("Minecraft server started", response);

        } catch (Exception e) {
            LOG.error("Failed to start server", e);
            return createErrorResponse("Failed to start server: " + e.getMessage());
        }
    }

    // ===============================
    // RESOURCE MANAGEMENT TOOLS
    // ===============================

    @Tool(name = "listTextures", description = "List all texture files in the workspace")
    public String listTextures() {
        LOG.info("Listing workspace textures...");
        
        try {
            if (workspacePath.isEmpty()) {
                return createErrorResponse("No workspace path configured");
            }

            Path texturesDir = Paths.get(workspacePath, "src", "main", "resources", "assets");
            List<String> textures = new ArrayList<>();

            if (Files.exists(texturesDir)) {
                Files.walk(texturesDir)
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String fileName = path.getFileName().toString().toLowerCase();
                            return fileName.endsWith(".png") || fileName.endsWith(".jpg") || 
                                   fileName.endsWith(".jpeg") || fileName.endsWith(".gif");
                        })
                        .forEach(path -> textures.add(texturesDir.relativize(path).toString()));
            }

            return createSuccessResponse("Textures retrieved", 
                    Map.of("textures", textures, "count", textures.size()));

        } catch (Exception e) {
            LOG.error("Failed to list textures", e);
            return createErrorResponse("Failed to list textures: " + e.getMessage());
        }
    }

    @Tool(name = "listSounds", description = "List all sound files in the workspace")
    public String listSounds() {
        LOG.info("Listing workspace sounds...");
        
        try {
            Map<String, Object> command = Map.of("action", "listSounds");
            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to list sounds: " + response.get("error"));
            }

            return createSuccessResponse("Sounds retrieved", response);

        } catch (Exception e) {
            LOG.error("Failed to list sounds", e);
            return createErrorResponse("Failed to list sounds: " + e.getMessage());
        }
    }

    @Tool(name = "listStructures", description = "List all structure files in the workspace")
    public String listStructures() {
        LOG.info("Listing workspace structures...");
        
        try {
            if (workspacePath.isEmpty()) {
                return createErrorResponse("No workspace path configured");
            }

            Path structuresDir = Paths.get(workspacePath, "src", "main", "resources", "data");
            List<String> structures = new ArrayList<>();

            if (Files.exists(structuresDir)) {
                Files.walk(structuresDir)
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".nbt"))
                        .forEach(path -> structures.add(structuresDir.relativize(path).toString()));
            }

            return createSuccessResponse("Structures retrieved", 
                    Map.of("structures", structures, "count", structures.size()));

        } catch (Exception e) {
            LOG.error("Failed to list structures", e);
            return createErrorResponse("Failed to list structures: " + e.getMessage());
        }
    }

    // ===============================
    // VARIABLE MANAGEMENT TOOLS
    // ===============================

    @Tool(name = "listVariables", description = "List all variables defined in the workspace")
    public String listVariables() {
        LOG.info("Listing workspace variables...");
        
        try {
            Map<String, Object> command = Map.of("action", "listVariables");
            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to list variables: " + response.get("error"));
            }

            return createSuccessResponse("Variables retrieved", response);

        } catch (Exception e) {
            LOG.error("Failed to list variables", e);
            return createErrorResponse("Failed to list variables: " + e.getMessage());
        }
    }

    @Tool(name = "createVariable", description = "Create a new workspace variable")
    public String createVariable(String name, String type, String scope) {
        LOG.info("Creating variable: " + name + " (" + type + ", " + scope + ")");
        
        if (name == null || name.trim().isEmpty()) {
            return createErrorResponse("Variable name is required");
        }
        
        if (type == null || type.trim().isEmpty()) {
            return createErrorResponse("Variable type is required");
        }
        
        if (scope == null || scope.trim().isEmpty()) {
            return createErrorResponse("Variable scope is required");
        }

        try {
            Map<String, Object> command = Map.of(
                "action", "createVariable",
                "name", name.trim(),
                "type", type.trim().toUpperCase(),
                "scope", scope.trim().toUpperCase()
            );

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to create variable: " + response.get("error"));
            }

            return createSuccessResponse("Variable created successfully", response);

        } catch (Exception e) {
            LOG.error("Failed to create variable: " + name + " (" + type + ", " + scope + ")", e);
            return createErrorResponse("Failed to create variable: " + e.getMessage());
        }
    }

    // ===============================
    // LOCALIZATION TOOLS
    // ===============================

    @Tool(name = "getLocalizations", description = "Get workspace localization entries")
    public String getLocalizations(String language) {
        LOG.info("Getting localizations for language: " + language);
        
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("action", "getLocalizations");
            if (language != null && !language.trim().isEmpty()) {
                command.put("language", language.trim());
            }

            Map<String, Object> response = ipcBridge.sendCommand(command);
            
            if (response.containsKey("error")) {
                return createErrorResponse("Failed to get localizations: " + response.get("error"));
            }

            return createSuccessResponse("Localizations retrieved", response);

        } catch (Exception e) {
            LOG.error("Failed to get localizations", e);
            return createErrorResponse("Failed to get localizations: " + e.getMessage());
        }
    }

    // ===============================
    // UTILITY METHODS
    // ===============================

    private String createSuccessResponse(String message, Map<String, Object> data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            response.put("timestamp", System.currentTimeMillis());
            
            if (data != null && !data.isEmpty()) {
                response.put("data", data);
            }
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            LOG.error("Failed to create success response", e);
            return "{\"success\":false,\"message\":\"Failed to create response\"}";
        }
    }

    private String createErrorResponse(String message) {
        try {
            Map<String, Object> response = Map.of(
                "success", false,
                "error", message,
                "timestamp", System.currentTimeMillis()
            );
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            LOG.error("Failed to create error response", e);
            return "{\"success\":false,\"error\":\"Failed to create error response\"}";
        }
    }
}
