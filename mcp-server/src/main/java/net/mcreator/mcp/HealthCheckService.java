package net.mcreator.mcp;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Health check service for monitoring the MCP server status and connectivity.
 */
@ApplicationScoped
public class HealthCheckService {

    private static final Logger LOG = Logger.getLogger(HealthCheckService.class);

    /**
     * Liveness probe - checks if the application is running
     */
    @Liveness
    public HealthCheckResponse liveness() {
        return HealthCheckResponse.up("MCP Server is alive");
    }

    /**
     * Readiness probe - checks if the application is ready to serve requests
     */
    @Readiness
    public HealthCheckResponse readiness() {
        try {
            // Check if tools are registered
            if (toolRegistry.getToolCount() == 0) {
                return HealthCheckResponse.down("No tools registered");
            }

            // Check IPC connectivity
            if (!ipcBridge.isAvailable()) {
                return HealthCheckResponse.named("readiness")
                        .withData("ipc_available", false)
                        .withData("tools_count", toolRegistry.getToolCount())
                        .up() // Still ready even if IPC is not available
                        .build();
            }

            return HealthCheckResponse.named("readiness")
                    .withData("ipc_available", true)
                    .withData("tools_count", toolRegistry.getToolCount())
                    .withData("statistics", toolRegistry.getStatistics())
                    .up()
                    .build();

        } catch (Exception e) {
            LOG.error("Readiness check failed", e);
            return HealthCheckResponse.down("Readiness check failed: " + e.getMessage());
        }
    }

    @Inject
    IPCBridgeService ipcBridge;

    @Inject
    MCPToolRegistry toolRegistry;
}
