package net.mcreator.MCreatorMCP;

import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.ui.MCreator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that implements MCreator tools for the MCP server.
 * This replaces the old IPC-based communication with direct integration.
 */
public class MCPToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-Tools");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Register all MCreator tools with the MCP server
     */
    public void registerTools(McpServer mcpServer, MCreator mcreator) {
        LOG.info("Registering MCreator tools with MCP server");

        // Workspace management tools
        mcpServer.registerHandler("buildWorkspace", params -> executeBuildWorkspace(mcreator));
        mcpServer.registerHandler("getWorkspaceInfo", params -> getWorkspaceInfo(mcreator));
        mcpServer.registerHandler("regenerateCode", params -> executeRegenerateCode(mcreator));

        // Element operations
        mcpServer.registerHandler("listModElements", params -> listModElements(mcreator, params));
        mcpServer.registerHandler("createElement", params -> createElement(mcreator, params));
        mcpServer.registerHandler("deleteElement", params -> deleteElement(mcreator, params));

        // Testing tools
        mcpServer.registerHandler("runClient", params -> executeRunClient(mcreator));
        mcpServer.registerHandler("runServer", params -> executeRunServer(mcreator));

        LOG.info("Registered {} MCreator tools", 8);
    }

    /**
     * Build workspace tool
     */
    private McpTypes.ToolResult executeBuildWorkspace(MCreator mcreator) {
        LOG.info("Executing buildWorkspace tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute build on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().buildWorkspace.doAction();
            });

            return createSuccessResult("Workspace build initiated successfully");

        } catch (Exception e) {
            LOG.error("Error building workspace", e);
            return createErrorResult("Failed to build workspace: " + e.getMessage());
        }
    }

    /**
     * Get workspace information
     */
    private McpTypes.ToolResult getWorkspaceInfo(MCreator mcreator) {
        LOG.info("Executing getWorkspaceInfo tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            Map<String, Object> info = new HashMap<>();
            info.put("name", workspace.getWorkspaceSettings().getModName());
            info.put("version", workspace.getWorkspaceSettings().getVersion());
            info.put("author", workspace.getWorkspaceSettings().getAuthor());
            info.put("description", workspace.getWorkspaceSettings().getDescription());
            info.put("mcreatorVersion", String.valueOf(workspace.getMCreatorVersion()));
            info.put("elementCount", workspace.getModElements().size());
            info.put("workspaceFolder", workspace.getWorkspaceFolder().getAbsolutePath());

            String infoJson = objectMapper.writeValueAsString(info);
            return createSuccessResult("Workspace information retrieved:\n" + infoJson);

        } catch (Exception e) {
            LOG.error("Error getting workspace info", e);
            return createErrorResult("Failed to get workspace info: " + e.getMessage());
        }
    }

    /**
     * Regenerate code tool
     */
    private McpTypes.ToolResult executeRegenerateCode(MCreator mcreator) {
        LOG.info("Executing regenerateCode tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute regenerate code on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().regenerateCode.doAction();
            });

            return createSuccessResult("Code regeneration initiated successfully");

        } catch (Exception e) {
            LOG.error("Error regenerating code", e);
            return createErrorResult("Failed to regenerate code: " + e.getMessage());
        }
    }

    /**
     * List mod elements tool
     */
    private McpTypes.ToolResult listModElements(MCreator mcreator, Map<String, Object> params) {
        LOG.info("Executing listModElements tool");

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            String elementType = (String) params.get("elementType");
            Collection<ModElement> elements = workspace.getModElements();

            // Filter by type if specified
            if (elementType != null && !elementType.trim().isEmpty()) {
                elements = elements.stream()
                    .filter(element -> element.getType().getRegistryName().equalsIgnoreCase(elementType.trim()))
                    .collect(Collectors.toList());
            }

            List<Map<String, Object>> elementList = elements.stream()
                .map(this::modElementToMap)
                .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("elements", elementList);
            result.put("count", elementList.size());
            result.put("filteredBy", elementType);

            String resultJson = objectMapper.writeValueAsString(result);
            return createSuccessResult("Found " + elementList.size() + " mod elements:\n" + resultJson);

        } catch (Exception e) {
            LOG.error("Error listing mod elements", e);
            return createErrorResult("Failed to list mod elements: " + e.getMessage());
        }
    }

    /**
     * Create element tool
     */
    private McpTypes.ToolResult createElement(MCreator mcreator, Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        String elementName = (String) params.get("elementName");

        LOG.info("Executing createElement tool: {} of type {}", elementName, elementType);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }

            if (elementType == null || elementType.trim().isEmpty()) {
                return createErrorResult("Element type is required");
            }

            // Find the ModElementType
            ModElementType type = null;
            for (ModElementType met : ModElementTypeLoader.getAllModElementTypes()) {
                if (met.getRegistryName().equalsIgnoreCase(elementType.trim())) {
                    type = met;
                    break;
                }
            }

            if (type == null) {
                return createErrorResult("Unknown element type: " + elementType);
            }

            // Check if element already exists
            if (workspace.getModElementByName(elementName.trim()) != null) {
                return createErrorResult("Element with name '" + elementName.trim() + "' already exists");
            }

            // Create the element on EDT
            final ModElementType finalType = type;
            final String finalName = elementName.trim();
            
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ModElement element = new ModElement(workspace, finalName, finalType);
                workspace.addModElement(element);
                workspace.markDirty();
            });

            return createSuccessResult("Element '" + elementName + "' of type '" + elementType + "' created successfully");

        } catch (Exception e) {
            LOG.error("Error creating element", e);
            return createErrorResult("Failed to create element: " + e.getMessage());
        }
    }

    /**
     * Delete element tool
     */
    private McpTypes.ToolResult deleteElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");

        LOG.info("Executing deleteElement tool: {}", elementName);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }

            ModElement element = workspace.getModElementByName(elementName.trim());
            if (element == null) {
                return createErrorResult("Element '" + elementName + "' not found");
            }

            // Delete the element on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                workspace.removeModElement(element);
                workspace.markDirty();
            });

            return createSuccessResult("Element '" + elementName + "' deleted successfully");

        } catch (Exception e) {
            LOG.error("Error deleting element", e);
            return createErrorResult("Failed to delete element: " + e.getMessage());
        }
    }

    /**
     * Run client tool
     */
    private McpTypes.ToolResult executeRunClient(MCreator mcreator) {
        LOG.info("Executing runClient tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute run client on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().runClient.doAction();
            });

            return createSuccessResult("Minecraft client started successfully");

        } catch (Exception e) {
            LOG.error("Error running client", e);
            return createErrorResult("Failed to run client: " + e.getMessage());
        }
    }

    /**
     * Run server tool
     */
    private McpTypes.ToolResult executeRunServer(MCreator mcreator) {
        LOG.info("Executing runServer tool");

        try {
            if (mcreator.getWorkspace() == null) {
                return createErrorResult("No workspace loaded");
            }

            // Execute run server on EDT
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().runServer.doAction();
            });

            return createSuccessResult("Minecraft server started successfully");

        } catch (Exception e) {
            LOG.error("Error running server", e);
            return createErrorResult("Failed to run server: " + e.getMessage());
        }
    }

    /**
     * Helper method to convert ModElement to Map
     */
    private Map<String, Object> modElementToMap(ModElement element) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", element.getName());
        map.put("type", element.getType().getRegistryName());
        map.put("isLocked", element.isCodeLocked());
        map.put("sortIndex", element.getName());
        return map;
    }

    /**
     * Helper method to create success result
     */
    private McpTypes.ToolResult createSuccessResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", message)
        );
        return new McpTypes.ToolResult(content, false);
    }

    /**
     * Helper method to create error result
     */
    private McpTypes.ToolResult createErrorResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", "Error: " + message)
        );
        return new McpTypes.ToolResult(content, true);
    }
}