package net.mcreator.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Administrative REST endpoint for managing and monitoring the MCP server.
 * Provides information about registered tools, IPC status, and server statistics.
 */
@Path("/admin")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final Logger LOG = Logger.getLogger(AdminResource.class);

    @Inject
    MCPToolRegistry toolRegistry;

    @Inject
    IPCBridgeService ipcBridge;

    @GET
    @Path("/status")
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", Instant.now().toString());
        status.put("status", "running");
        status.put("toolsRegistered", toolRegistry.getToolCount());
        status.put("ipcAvailable", ipcBridge.isAvailable());
        status.put("ipcStatistics", ipcBridge.getStatistics());

        return Response.ok(status).build();
    }

    @GET
    @Path("/tools")
    public Response getTools() {
        Collection<MCPToolInfo> tools = toolRegistry.getAllTools();
        
        Map<String, Object> response = new HashMap<>();
        response.put("tools", tools);
        response.put("count", tools.size());
        response.put("statistics", toolRegistry.getStatistics());

        return Response.ok(response).build();
    }

    @GET
    @Path("/tools/{toolName}")
    public Response getTool(@PathParam("toolName") String toolName) {
        MCPToolInfo tool = toolRegistry.getTool(toolName);
        
        if (tool == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Tool not found: " + toolName))
                    .build();
        }

        return Response.ok(tool).build();
    }

    @GET
    @Path("/tools/category/{category}")
    public Response getToolsByCategory(@PathParam("category") String category) {
        Collection<MCPToolInfo> tools = toolRegistry.getToolsByCategory(category);
        
        Map<String, Object> response = new HashMap<>();
        response.put("category", category);
        response.put("tools", tools);
        response.put("count", tools.size());

        return Response.ok(response).build();
    }

    @POST
    @Path("/ipc/test")
    public Response testIPC() {
        try {
            Map<String, Object> testCommand = Map.of("action", "ping");
            Map<String, Object> response = ipcBridge.sendCommand(testCommand);
            
            return Response.ok(Map.of(
                "success", !response.containsKey("error"),
                "response", response,
                "timestamp", Instant.now().toString()
            )).build();
            
        } catch (Exception e) {
            LOG.error("IPC test failed", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "IPC test failed: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/metrics")
    public Response getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timestamp", Instant.now().toString());
        metrics.put("uptime", System.currentTimeMillis()); // Simple uptime approximation
        metrics.put("toolRegistry", toolRegistry.getStatistics());
        metrics.put("ipcBridge", ipcBridge.getStatistics());
        metrics.put("jvm", Map.of(
            "totalMemory", Runtime.getRuntime().totalMemory(),
            "freeMemory", Runtime.getRuntime().freeMemory(),
            "maxMemory", Runtime.getRuntime().maxMemory(),
            "availableProcessors", Runtime.getRuntime().availableProcessors()
        ));

        return Response.ok(metrics).build();
    }

    @DELETE
    @Path("/tools/cache")
    public Response clearToolsCache() {
        try {
            toolRegistry.clearAllTools();
            
            // Re-discover tools
            // Note: This would require injecting ToolDiscoveryService
            
            return Response.ok(Map.of(
                "success", true,
                "message", "Tools cache cleared",
                "timestamp", Instant.now().toString()
            )).build();
            
        } catch (Exception e) {
            LOG.error("Failed to clear tools cache", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to clear cache: " + e.getMessage()))
                    .build();
        }
    }
}
