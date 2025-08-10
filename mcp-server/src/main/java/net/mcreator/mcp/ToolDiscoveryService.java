package net.mcreator.mcp;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for dynamically discovering MCreator APIs and generating 
 * corresponding MCP tools. This service uses reflection to scan MCreator classes
 * and automatically expose relevant methods as MCP tools.
 */
@ApplicationScoped
public class ToolDiscoveryService {

    private static final Logger LOG = Logger.getLogger(ToolDiscoveryService.class);

    @ConfigProperty(name = "mcreator.tools.include-deprecated", defaultValue = "false")
    boolean includeDeprecated;

    @Inject
    MCPToolRegistry toolRegistry;

    private final Map<String, MCPToolInfo> discoveredTools = new ConcurrentHashMap<>();

    /**
     * Discovers MCreator APIs and registers them as MCP tools
     * @return number of tools discovered and registered
     */
    public int discoverAndRegisterTools() {
        LOG.info("Starting MCreator API discovery...");
        
        try {
            // Register core built-in tools first
            registerBuiltinTools();
            
            // Discover workspace management APIs
            discoverWorkspaceAPIs();
            
            // Discover action registry APIs
            discoverActionAPIs();
            
            // Discover generator APIs
            discoverGeneratorAPIs();
            
            // Discover element management APIs
            discoverElementAPIs();
            
            LOG.info("API discovery completed. Found " + discoveredTools.size() + " tools total");
            return discoveredTools.size();
            
        } catch (Exception e) {
            LOG.error("Error during API discovery", e);
            return 0;
        }
    }

    private void registerBuiltinTools() {
        LOG.debug("Registering built-in MCP tools...");
        
        // Workspace management tools
        registerTool("buildWorkspace", "Build the current MCreator workspace", 
                "Triggers a full build of the MCreator workspace including code generation and compilation",
                List.of(), "workspace");
                
        registerTool("getWorkspaceInfo", "Get workspace information", 
                "Returns detailed information about the current workspace including settings, elements, and metadata",
                List.of(), "workspace");
                
        registerTool("listModElements", "List all mod elements", 
                "Returns a list of all mod elements in the workspace with their types and properties",
                List.of(new MCPToolParameter("elementType", "string", "Optional type filter", false)), "elements");
                
        registerTool("openElement", "Open a mod element", 
                "Opens the specified mod element in the MCreator interface",
                List.of(
                    new MCPToolParameter("elementName", "string", "Name of the element to open", true)
                ), "elements");
                
        registerTool("createElement", "Create a new mod element", 
                "Creates a new mod element of the specified type",
                List.of(
                    new MCPToolParameter("elementType", "string", "Type of element to create", true),
                    new MCPToolParameter("elementName", "string", "Name for the new element", true)
                ), "elements");
                
        registerTool("deleteElement", "Delete a mod element", 
                "Deletes the specified mod element from the workspace",
                List.of(
                    new MCPToolParameter("elementName", "string", "Name of the element to delete", true)
                ), "elements");
                
        // Code generation tools
        registerTool("regenerateCode", "Regenerate workspace code", 
                "Regenerates all code for the workspace without building",
                List.of(), "generation");
                
        registerTool("runClient", "Run Minecraft client", 
                "Starts the Minecraft client with the mod loaded",
                List.of(), "testing");
                
        registerTool("runServer", "Run Minecraft server", 
                "Starts the Minecraft server with the mod loaded",
                List.of(), "testing");
                
        // Resource management tools
        registerTool("listTextures", "List workspace textures", 
                "Returns a list of all texture files in the workspace",
                List.of(), "resources");
                
        registerTool("listSounds", "List workspace sounds", 
                "Returns a list of all sound files in the workspace",
                List.of(), "resources");
                
        registerTool("listStructures", "List workspace structures", 
                "Returns a list of all structure files in the workspace",
                List.of(), "resources");
                
        // Variable and localization tools
        registerTool("listVariables", "List workspace variables", 
                "Returns a list of all variables defined in the workspace",
                List.of(), "variables");
                
        registerTool("createVariable", "Create a workspace variable", 
                "Creates a new variable in the workspace",
                List.of(
                    new MCPToolParameter("name", "string", "Variable name", true),
                    new MCPToolParameter("type", "string", "Variable type (NUMBER, LOGIC, STRING)", true),
                    new MCPToolParameter("scope", "string", "Variable scope (GLOBAL_WORLD, GLOBAL_MAP, PLAYER_LIFETIME, PLAYER_PERSISTENT)", true)
                ), "variables");
                
        registerTool("getLocalizations", "Get workspace localizations", 
                "Returns all localization entries for the workspace",
                List.of(
                    new MCPToolParameter("language", "string", "Language code (e.g. 'en_us')", false)
                ), "localization");
                
        LOG.info("Registered " + discoveredTools.size() + " built-in tools");
    }

    private void discoverWorkspaceAPIs() {
        LOG.debug("Discovering Workspace APIs...");
        
        try {
            Class<?> workspaceClass = Class.forName("net.mcreator.workspace.Workspace");
            
            for (Method method : workspaceClass.getDeclaredMethods()) {
                if (isValidAPIMethod(method)) {
                    String toolName = "workspace_" + method.getName();
                    String description = generateMethodDescription(method);
                    List<MCPToolParameter> parameters = generateMethodParameters(method);
                    
                    registerTool(toolName, description, description, parameters, "workspace-api");
                }
            }
            
        } catch (ClassNotFoundException e) {
            LOG.warn("Workspace class not found during discovery: " + e.getMessage());
        }
    }

    private void discoverActionAPIs() {
        LOG.debug("Discovering Action Registry APIs...");
        
        try {
            Class<?> actionRegistryClass = Class.forName("net.mcreator.ui.action.ActionRegistry");
            
            // Get all action fields (public final BasicAction fields)
            Arrays.stream(actionRegistryClass.getDeclaredFields())
                .filter(field -> field.getType().getSimpleName().contains("Action"))
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .forEach(field -> {
                    String toolName = "action_" + field.getName();
                    String description = "Execute " + field.getName() + " action";
                    
                    registerTool(toolName, description, description, List.of(), "actions");
                });
                
        } catch (ClassNotFoundException e) {
            LOG.warn("ActionRegistry class not found during discovery: " + e.getMessage());
        }
    }

    private void discoverGeneratorAPIs() {
        LOG.debug("Discovering Generator APIs...");
        
        try {
            Class<?> generatorClass = Class.forName("net.mcreator.generator.Generator");
            
            for (Method method : generatorClass.getDeclaredMethods()) {
                if (isValidAPIMethod(method) && 
                    (method.getName().startsWith("generate") || 
                     method.getName().startsWith("build") ||
                     method.getName().startsWith("export"))) {
                    
                    String toolName = "generator_" + method.getName();
                    String description = generateMethodDescription(method);
                    List<MCPToolParameter> parameters = generateMethodParameters(method);
                    
                    registerTool(toolName, description, description, parameters, "generator");
                }
            }
            
        } catch (ClassNotFoundException e) {
            LOG.warn("Generator class not found during discovery: " + e.getMessage());
        }
    }

    private void discoverElementAPIs() {
        LOG.debug("Discovering ModElement APIs...");
        
        try {
            // Discover ModElementType enum
            Class<?> modElementTypeClass = Class.forName("net.mcreator.element.ModElementType");
            if (modElementTypeClass.isEnum()) {
                Object[] elementTypes = modElementTypeClass.getEnumConstants();
                
                for (Object elementType : elementTypes) {
                    String typeName = elementType.toString().toLowerCase();
                    
                    registerTool("create_" + typeName, "Create " + typeName + " element", 
                            "Creates a new " + typeName + " mod element",
                            List.of(
                                new MCPToolParameter("name", "string", "Element name", true)
                            ), "element-creation");
                }
            }
            
        } catch (ClassNotFoundException e) {
            LOG.warn("ModElementType class not found during discovery: " + e.getMessage());
        }
    }

    private boolean isValidAPIMethod(Method method) {
        int modifiers = method.getModifiers();
        
        // Must be public
        if (!Modifier.isPublic(modifiers)) {
            return false;
        }
        
        // Skip static methods for now
        if (Modifier.isStatic(modifiers)) {
            return false;
        }
        
        // Skip deprecated methods unless explicitly enabled
        if (!includeDeprecated && method.isAnnotationPresent(Deprecated.class)) {
            return false;
        }
        
        // Skip methods that return void and have no parameters (likely not useful as tools)
        if (method.getReturnType() == void.class && method.getParameterCount() == 0) {
            return false;
        }
        
        // Skip getters and setters for basic types
        String methodName = method.getName();
        if ((methodName.startsWith("get") || methodName.startsWith("is") || methodName.startsWith("set")) 
            && method.getParameterCount() <= 1) {
            return false;
        }
        
        return true;
    }

    private String generateMethodDescription(Method method) {
        String methodName = method.getName();
        
        // Convert camelCase to readable description
        String readable = methodName.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        
        return "Execute " + readable + " operation";
    }

    private List<MCPToolParameter> generateMethodParameters(Method method) {
        List<MCPToolParameter> parameters = new ArrayList<>();
        
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] params = method.getParameters();
        
        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = params[i].getName();
            String paramType = mapJavaTypeToMCPType(paramTypes[i]);
            String description = "Parameter " + paramName + " of type " + paramTypes[i].getSimpleName();
            
            parameters.add(new MCPToolParameter(paramName, paramType, description, true));
        }
        
        return parameters;
    }

    private String mapJavaTypeToMCPType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == int.class || javaType == Integer.class) return "number";
        if (javaType == long.class || javaType == Long.class) return "number";
        if (javaType == double.class || javaType == Double.class) return "number";
        if (javaType == float.class || javaType == Float.class) return "number";
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType.isArray()) return "array";
        if (List.class.isAssignableFrom(javaType)) return "array";
        if (Map.class.isAssignableFrom(javaType)) return "object";
        
        return "object";
    }

    private void registerTool(String name, String description, String detailedDescription, 
                             List<MCPToolParameter> parameters, String category) {
        MCPToolInfo toolInfo = new MCPToolInfo(name, description, detailedDescription, parameters, category);
        discoveredTools.put(name, toolInfo);
        toolRegistry.registerTool(toolInfo);
        
        LOG.debug("Registered tool: " + name + " (" + category + ")");
    }

    public Map<String, MCPToolInfo> getDiscoveredTools() {
        return new HashMap<>(discoveredTools);
    }

    public MCPToolInfo getTool(String name) {
        return discoveredTools.get(name);
    }
}
