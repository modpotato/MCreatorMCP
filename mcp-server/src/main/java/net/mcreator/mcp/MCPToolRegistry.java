package net.mcreator.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing MCP tools. This service maintains a registry of all available
 * tools and provides methods to register, lookup, and manage tool metadata.
 */
@ApplicationScoped
public class MCPToolRegistry {

    private static final Logger LOG = Logger.getLogger(MCPToolRegistry.class);

    private final Map<String, MCPToolInfo> tools = new ConcurrentHashMap<>();
    private final Map<String, Object> toolInstances = new ConcurrentHashMap<>();

    /**
     * Register a tool in the registry
     * @param toolInfo The tool information
     */
    public void registerTool(MCPToolInfo toolInfo) {
        if (toolInfo == null || toolInfo.getName() == null) {
            LOG.warn("Attempted to register null tool or tool with null name");
            return;
        }

        if (tools.containsKey(toolInfo.getName())) {
            LOG.warn("Tool is already registered, overwriting: " + toolInfo.getName());
        }

        tools.put(toolInfo.getName(), toolInfo);
        LOG.debug("Registered tool: " + toolInfo.getName());
    }

    /**
     * Get tool information by name
     * @param name The tool name
     * @return The tool information or null if not found
     */
    public MCPToolInfo getTool(String name) {
        return tools.get(name);
    }

    /**
     * Check if a tool is registered
     * @param name The tool name
     * @return true if the tool is registered
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Get all registered tools
     * @return Collection of all tool information
     */
    public Collection<MCPToolInfo> getAllTools() {
        return tools.values();
    }

    /**
     * Get tools by category
     * @param category The category to filter by
     * @return Collection of tools in the specified category
     */
    public Collection<MCPToolInfo> getToolsByCategory(String category) {
        return tools.values().stream()
                .filter(tool -> category.equals(tool.getCategory()))
                .toList();
    }

    /**
     * Get the count of registered tools
     * @return Number of registered tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Unregister a tool
     * @param name The tool name to unregister
     * @return true if the tool was removed
     */
    public boolean unregisterTool(String name) {
        boolean removed = tools.remove(name) != null;
        toolInstances.remove(name);
        
        if (removed) {
            LOG.debug("Unregistered tool: " + name);
        }
        
        return removed;
    }

    /**
     * Clear all registered tools
     */
    public void clearAllTools() {
        int count = tools.size();
        tools.clear();
        toolInstances.clear();
        LOG.info("Cleared " + count + " tools from registry");
    }

    /**
     * Store a tool instance for later use
     * @param name The tool name
     * @param instance The tool instance
     */
    public void setToolInstance(String name, Object instance) {
        toolInstances.put(name, instance);
    }

    /**
     * Get a stored tool instance
     * @param name The tool name
     * @return The tool instance or null if not found
     */
    public Object getToolInstance(String name) {
        return toolInstances.get(name);
    }

    /**
     * Get registry statistics
     * @return String with registry statistics
     */
    public String getStatistics() {
        Map<String, Long> categoryStats = tools.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MCPToolInfo::getCategory,
                        java.util.stream.Collectors.counting()
                ));

        StringBuilder stats = new StringBuilder();
        stats.append("MCP Tool Registry Statistics:\n");
        stats.append("Total tools: ").append(tools.size()).append("\n");
        stats.append("Categories:\n");
        
        categoryStats.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> stats.append("  ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append("\n"));

        return stats.toString();
    }
}
