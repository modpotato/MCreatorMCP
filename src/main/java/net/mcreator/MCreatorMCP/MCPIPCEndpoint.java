package net.mcreator.MCreatorMCP;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.elements.VariableElement;
import net.mcreator.workspace.elements.VariableType;
import net.mcreator.workspace.elements.VariableTypeLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * IPC endpoint that runs inside MCreator to handle commands from the MCP server.
 * This provides the bridge between the external MCP server and MCreator's internal APIs.
 */
public class MCPIPCEndpoint {

    private static final Logger LOG = LogManager.getLogger("MCP-IPC");

    private HttpServer server;
    private MCreator mcreator;
    private final Gson gson = new Gson();

    /**
     * Start the IPC endpoint HTTP server
     * @param port The port to listen on
     * @param mcreator The MCreator instance
     */
    public void start(int port, MCreator mcreator) {
        this.mcreator = mcreator;

        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext("/mcp-ipc", new IPCHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            LOG.info("MCP IPC endpoint started on port {}", port);
        } catch (IOException e) {
            LOG.error("Failed to start IPC endpoint", e);
            throw new RuntimeException("Failed to start IPC endpoint", e);
        }
    }

    /**
     * Stop the IPC endpoint
     */
    public void stop() {
        if (server != null) {
            LOG.info("Stopping MCP IPC endpoint...");
            server.stop(2);
            server = null;
            LOG.info("MCP IPC endpoint stopped");
        }
    }

    private class IPCHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            try {
                // Read request body
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject command = JsonParser.parseString(requestBody).getAsJsonObject();

                LOG.debug("Received IPC command: {}", command.get("action"));

                // Process command
                Map<String, Object> response = processCommand(command);

                // Send response
                sendResponse(exchange, 200, response);

            } catch (Exception e) {
                LOG.error("Error processing IPC command", e);
                sendResponse(exchange, 500, Map.of("error", "Internal server error: " + e.getMessage()));
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, Map<String, Object> response) throws IOException {
            Map<String, Object> resp = new LinkedHashMap<>(response); // create mutable copy so we can add fields
            resp.put("timestamp", System.currentTimeMillis());

            String responseBody = gson.toJson(resp);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private Map<String, Object> processCommand(JsonObject command) {
        String action = command.get("action").getAsString();

        try {
            return switch (action) {
                case "ping" -> Map.of("pong", true, "mcreator_version", "2025.2");

                case "buildWorkspace" -> executeBuildWorkspace();

                case "getWorkspaceStats" -> getWorkspaceStats();

                case "getWorkspaceSettings" -> getWorkspaceSettings();

                case "regenerateCode" -> executeRegenerateCode();

                case "listModElements" -> listModElements(command);

                case "openElement" -> openElement(command);

                case "createElement" -> createElement(command);

                case "deleteElement" -> deleteElement(command);

                case "runClient" -> executeRunClient();

                case "runServer" -> executeRunServer();

                case "listSounds" -> listSounds();

                case "listVariables" -> listVariables();

                case "createVariable" -> createVariable(command);

                case "getLocalizations" -> getLocalizations(command);

                default -> Map.of("error", "Unknown action: " + action);
            };
        } catch (Exception e) {
            LOG.error("Error executing command: {}", action, e);
            return Map.of("error", "Command execution failed: " + e.getMessage());
        }
    }

    // ===============================
    // WORKSPACE OPERATIONS
    // ===============================

    private Map<String, Object> executeBuildWorkspace() {
        return executeOnSwingThread(() -> {
            try {
                mcreator.getActionRegistry().buildWorkspace.doAction();
                return Map.of("success", true, "message", "Build started", "buildId", UUID.randomUUID().toString());
            } catch (Exception e) {
                return Map.of("error", "Failed to start build: " + e.getMessage());
            }
        });
    }

    private Map<String, Object> getWorkspaceStats() {
        Workspace workspace = mcreator.getWorkspace();

        Map<String, Object> stats = new HashMap<>();
        stats.put("modElementCount", workspace.getModElements().size());
        stats.put("variableCount", workspace.getVariableElements().size());
        stats.put("soundCount", workspace.getSoundElements().size());
        stats.put("tagCount", workspace.getTagElements().size());
        stats.put("languageCount", workspace.getLanguageMap().size());

        // Count elements by type
        Map<String, Long> elementsByType = workspace.getModElements().stream()
                .collect(Collectors.groupingBy(
                        element -> element.getType().getRegistryName(),
                        Collectors.counting()
                ));
        stats.put("elementsByType", elementsByType);

        stats.put("workspaceName", workspace.getWorkspaceSettings().getModName());
        stats.put("workspaceVersion", workspace.getWorkspaceSettings().getVersion());

        return stats;
    }

    private Map<String, Object> getWorkspaceSettings() {
        Workspace workspace = mcreator.getWorkspace();

        Map<String, Object> settings = new HashMap<>();
        settings.put("modName", workspace.getWorkspaceSettings().getModName());
        settings.put("modId", workspace.getWorkspaceSettings().getModID());
        settings.put("version", workspace.getWorkspaceSettings().getVersion());
        settings.put("description", workspace.getWorkspaceSettings().getDescription());
        settings.put("author", workspace.getWorkspaceSettings().getAuthor());
        settings.put("websiteURL", workspace.getWorkspaceSettings().getWebsiteURL());
        settings.put("license", workspace.getWorkspaceSettings().getLicense());
        settings.put("minecraftVersion", workspace.getGenerator().getGeneratorMinecraftVersion());

        return settings;
    }

    private Map<String, Object> executeRegenerateCode() {
        return executeOnSwingThread(() -> {
            try {
                mcreator.getActionRegistry().regenerateCode.doAction();
                return Map.of("success", true, "message", "Code regeneration started");
            } catch (Exception e) {
                return Map.of("error", "Failed to regenerate code: " + e.getMessage());
            }
        });
    }

    // ===============================
    // ELEMENT OPERATIONS
    // ===============================

    private Map<String, Object> listModElements(JsonObject command) {
        Workspace workspace = mcreator.getWorkspace();

        String elementTypeFilter = command.has("elementType") ? command.get("elementType").getAsString() : null;

        List<Map<String, Object>> elements = workspace.getModElements().stream()
                .filter(element -> elementTypeFilter == null || 
                       element.getType().getRegistryName().equalsIgnoreCase(elementTypeFilter))
                .map(this::elementToMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("elements", elements);
        result.put("totalCount", elements.size());
        result.put("filter", elementTypeFilter);

        return result;
    }

    private Map<String, Object> openElement(JsonObject command) {
        String elementName = command.get("elementName").getAsString();
        Workspace workspace = mcreator.getWorkspace();

        ModElement element = workspace.getModElementByName(elementName);
        if (element == null) {
            return Map.of("error", "Element not found: " + elementName);
        }

        return executeOnSwingThread(() -> {
            try {
                var gui = element.getType().getModElementGUI(mcreator, element, true);
                if (gui != null) {
                    gui.showView();
                }
                return Map.of("success", true, "message", "Element opened: " + elementName);
            } catch (Exception e) {
                return Map.of("error", "Failed to open element: " + e.getMessage());
            }
        });
    }

    private Map<String, Object> createElement(JsonObject command) {
        String elementType = command.get("elementType").getAsString();
        String elementName = command.get("elementName").getAsString();

        Workspace workspace = mcreator.getWorkspace();

        // Find the element type
        ModElementType<?> modElementType;
        try {
            modElementType = ModElementTypeLoader.getModElementType(elementType.toLowerCase());
        } catch (Exception e) {
            return Map.of("error", "Unknown element type: " + elementType);
        }

        final ModElementType<?> finalType = modElementType;
        return executeOnSwingThread(() -> {
            try {
                ModElement element = new ModElement(workspace, elementName, finalType);
                workspace.addModElement(element);
                workspace.markDirty();

                return Map.of("success", true, "message", "Element created: " + elementName, 
                            "elementType", elementType);
            } catch (Exception e) {
                return Map.of("error", "Failed to create element: " + e.getMessage());
            }
        });
    }

    private Map<String, Object> deleteElement(JsonObject command) {
        String elementName = command.get("elementName").getAsString();
        Workspace workspace = mcreator.getWorkspace();


        ModElement element = workspace.getModElementByName(elementName);
        if (element == null) {
            return Map.of("error", "Element not found: " + elementName);
        }

        return executeOnSwingThread(() -> {
            try {
                workspace.removeModElement(element);
                workspace.markDirty();
                return Map.of("success", true, "message", "Element deleted: " + elementName);
            } catch (Exception e) {
                return Map.of("error", "Failed to delete element: " + e.getMessage());
            }
        });
    }

    // ===============================
    // TESTING OPERATIONS
    // ===============================

    private Map<String, Object> executeRunClient() {
        return executeOnSwingThread(() -> {
            try {
                mcreator.getActionRegistry().runClient.doAction();
                return Map.of("success", true, "message", "Client started");
            } catch (Exception e) {
                return Map.of("error", "Failed to start client: " + e.getMessage());
            }
        });
    }

    private Map<String, Object> executeRunServer() {
        return executeOnSwingThread(() -> {
            try {
                mcreator.getActionRegistry().runServer.doAction();
                return Map.of("success", true, "message", "Server started");
            } catch (Exception e) {
                return Map.of("error", "Failed to start server: " + e.getMessage());
            }
        });
    }

    // ===============================
    // RESOURCE OPERATIONS
    // ===============================

    private Map<String, Object> listSounds() {
        Workspace workspace = mcreator.getWorkspace();

        List<Map<String, Object>> sounds = workspace.getSoundElements().stream()
                .map(this::soundToMap)
                .collect(Collectors.toList());

        return Map.of("sounds", sounds, "count", sounds.size());
    }

    // ===============================
    // VARIABLE OPERATIONS
    // ===============================

    private Map<String, Object> listVariables() {
        Workspace workspace = mcreator.getWorkspace();

        List<Map<String, Object>> variables = workspace.getVariableElements().stream()
                .map(this::variableToMap)
                .collect(Collectors.toList());

        return Map.of("variables", variables, "count", variables.size());
    }

    private Map<String, Object> createVariable(JsonObject command) {
        String name = command.get("name").getAsString();
        String type = command.get("type").getAsString();
        String scope = command.get("scope").getAsString();

        Workspace workspace = mcreator.getWorkspace();

        try {
            if (VariableTypeLoader.INSTANCE == null) {
                VariableTypeLoader.loadVariableTypes();
            }
            VariableType variableType = VariableTypeLoader.INSTANCE.fromName(type);
            if (variableType == null) {
                return Map.of("error", "Invalid variable type: " + type);
            }
            VariableType.Scope variableScope = VariableType.Scope.valueOf(scope.toUpperCase());

            return executeOnSwingThread(() -> {
                try {
                    VariableElement variable = new VariableElement(name);
                    variable.setType(variableType);
                    variable.setScope(variableScope);
                    workspace.addVariableElement(variable);
                    workspace.markDirty();

                    return Map.of("success", true, "message", "Variable created: " + name,
                                "name", name, "type", variableType.getName(), "scope", variableScope.name());
                } catch (Exception e) {
                    return Map.of("error", "Failed to create variable: " + e.getMessage());
                }
            });
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid variable type or scope: " + e.getMessage());
        }
    }

    // ===============================
    // LOCALIZATION OPERATIONS
    // ===============================

    private Map<String, Object> getLocalizations(JsonObject command) {
        Workspace workspace = mcreator.getWorkspace();

        String language = command.has("language") ? command.get("language").getAsString() : null;
        Map<String, LinkedHashMap<String, String>> languageMap = workspace.getLanguageMap();

        if (language != null) {
            LinkedHashMap<String, String> langData = languageMap.get(language);
            if (langData == null) {
                return Map.of("error", "Language not found: " + language);
            }
            return Map.of("language", language, "entries", langData, "count", langData.size());
        } else {
            return Map.of("languages", languageMap, "availableLanguages", languageMap.keySet());
        }
    }

    // ===============================
    // UTILITY METHODS
    // ===============================

    private Map<String, Object> elementToMap(ModElement element) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", element.getName());
        map.put("type", element.getType().getRegistryName());
        map.put("isLocked", element.isCodeLocked());
        return map;
    }

    private Map<String, Object> soundToMap(SoundElement sound) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", sound.getName());
        map.put("category", sound.getCategory());
        return map;
    }

    private Map<String, Object> variableToMap(VariableElement variable) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", variable.getName());
        map.put("type", variable.getType() != null ? variable.getType().getName() : null);
        map.put("scope", variable.getScope().name());
        return map;
    }

    private Map<String, Object> executeOnSwingThread(java.util.function.Supplier<Map<String, Object>> operation) {
        // If already on EDT, execute directly
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return operation.get();
            } catch (Exception e) {
                return Map.of("error", "Operation failed: " + e.getMessage());
            }
        }

        final java.util.concurrent.atomic.AtomicReference<Map<String, Object>> result = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Exception> exception = new java.util.concurrent.atomic.AtomicReference<>();

        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(operation.get());
                } catch (Exception e) {
                    exception.set(e);
                }
            });
        } catch (Exception e) {
            return Map.of("error", "Operation failed: " + e.getMessage());
        }

        if (exception.get() != null) {
            return Map.of("error", "Operation failed: " + exception.get().getMessage());
        }

        return result.get();
    }
}
