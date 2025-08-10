package net.mcreator.mcp;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Main MCP Server application that manages the lifecycle and configuration
 * of the Model Context Protocol server for MCreator integration.
 */
@ApplicationScoped
public class MCPServerApplication {

    private static final Logger LOG = Logger.getLogger(MCPServerApplication.class);

    @ConfigProperty(name = "mcreator.workspace.path", defaultValue = "")
    String workspacePath;

    @ConfigProperty(name = "mcreator.ipc.port", defaultValue = "9876")
    int ipcPort;

    @ConfigProperty(name = "mcreator.ipc.enabled", defaultValue = "true")
    boolean ipcEnabled;

    @ConfigProperty(name = "mcreator.tools.auto-discovery", defaultValue = "true")
    boolean autoDiscovery;

    @Inject
    ToolDiscoveryService toolDiscoveryService;

    @Inject
    IPCBridgeService ipcBridgeService;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Starting MCreator MCP Server");
        LOG.info("Workspace path: " + (workspacePath.isEmpty() ? "Not set" : workspacePath));
        LOG.info("IPC enabled: " + ipcEnabled);
        LOG.info("Auto-discovery enabled: " + autoDiscovery);

        try {
            // Initialize IPC bridge if enabled
            if (ipcEnabled) {
                ipcBridgeService.initialize(ipcPort);
                LOG.info("IPC bridge initialized on port " + ipcPort);
            }

            // Discover and register tools if auto-discovery is enabled
            if (autoDiscovery) {
                int discoveredTools = toolDiscoveryService.discoverAndRegisterTools();
                LOG.info("Discovered and registered " + discoveredTools + " MCreator tools");
            }

            LOG.info("MCreator MCP Server started successfully");
        } catch (Exception e) {
            LOG.error("Failed to start MCreator MCP Server", e);
            throw new RuntimeException("Startup failed", e);
        }
    }
}
