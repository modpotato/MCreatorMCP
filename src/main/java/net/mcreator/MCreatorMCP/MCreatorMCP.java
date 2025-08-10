package net.mcreator.MCreatorMCP;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.ui.action.BasicAction;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicReference;
import net.mcreator.io.zip.ZipIO;

public class MCreatorMCP extends JavaPlugin {

    private static final Logger LOG = LogManager.getLogger("MCreatorMCP");
    
    private final AtomicReference<Process> mcpServerProcess = new AtomicReference<>();
    private MCPIPCEndpoint ipcEndpoint;
    private volatile int currentHttpPort = 5175;
    private volatile int currentIpcPort = 9876;

    public MCreatorMCP(Plugin plugin) {
        super(plugin);

        // Initialize IPC endpoint
        ipcEndpoint = new MCPIPCEndpoint();

        addListener(MCreatorLoadedEvent.class, event -> SwingUtilities.invokeLater(() -> {
            // Start MCP server
            startMCPServer(event);

            // Create demo action (keep existing functionality)
            BasicAction demoAction = new BasicAction(event.getMCreator().getActionRegistry(),
                    L10N.t("plugin.demojava.menu.button"),
                    e -> event.getMCreator().getActionRegistry().buildWorkspace.doAction());
            demoAction.setIcon(UIRES.get("16px.play"));

            // Create MCP status action
            BasicAction mcpStatusAction = new BasicAction(event.getMCreator().getActionRegistry(),
                    "MCP Server Status",
                    e -> showMCPStatus());
            mcpStatusAction.setIcon(UIRES.get("16px.info"));

            // Create MCP restart action
            BasicAction mcpRestartAction = new BasicAction(event.getMCreator().getActionRegistry(),
                    "Restart MCP Server",
                    e -> restartMCPServer(event));
            mcpRestartAction.setIcon(UIRES.get("16px.reset"));

            // Build menu
            JMenu menu = new JMenu(L10N.t("plugin.demojava.menu.main"));
            menu.add(demoAction);
            menu.addSeparator();
            menu.add(mcpStatusAction);
            menu.add(mcpRestartAction);

            event.getMCreator().getMainMenuBar().add(menu);
            event.getMCreator().getToolBar().addToRightToolbar(demoAction);
        }));

        LOG.info("MCreator MCP Plugin loaded - ready to start MCP server");
    }

    private void startMCPServer(MCreatorLoadedEvent event) {
        try {
            // Stop existing server if running
            stopMCPServer();

            String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            File pluginFile = getPlugin().getFile();
            File pluginRoot;
            if (pluginFile.isDirectory()) {
                pluginRoot = pluginFile;
            } else {
                File cacheDir = new File(System.getProperty("java.io.tmpdir"),
                        "mcreator-mcp/" + getPlugin().getID());
                boolean hasRunJar =
                        new File(cacheDir, "lib/mcp-server/quarkus-run.jar").isFile() ||
                        new File(cacheDir, "lib/mcp-server/quarkus-app/quarkus-run.jar").isFile();
                if (!hasRunJar) {
                    ZipIO.unzip(pluginFile.getAbsolutePath(), cacheDir.getAbsolutePath());
                }
                pluginRoot = cacheDir;
            }
            File mcpServerJar = new File(pluginRoot, "lib/mcp-server/quarkus-run.jar");
            if (!mcpServerJar.isFile()) {
                LOG.error("MCP server jar not found at: {}", mcpServerJar.getAbsolutePath());
                showErrorDialog("MCP Server Not Found", "MCP server jar not found. Please ensure the plugin was built correctly.");
                return;
            }
            File serverHome = mcpServerJar.getParentFile();

            if (!mcpServerJar.exists()) {
                LOG.error("MCP server jar not found at: {}", mcpServerJar.getAbsolutePath());
                showErrorDialog("MCP Server Not Found", 
                    "MCP server jar not found. Please ensure the plugin was built correctly.");
                return;
            }

            // Find free ports
            int httpPort = findFreePort(5175);
            int ipcPort = findFreePort(9876);
            currentHttpPort = httpPort;
            currentIpcPort = ipcPort;

            // Configure environment
            ProcessBuilder pb = new ProcessBuilder(
                javaExe,
                "-Dquarkus.http.port=" + httpPort,
                "-Dmcreator.workspace.path=" + event.getMCreator().getWorkspaceFolder().getAbsolutePath(),
                "-Dmcreator.ipc.port=" + ipcPort,
                "-Dmcreator.ipc.enabled=true",
                "-Dmcreator.tools.auto-discovery=true",
                "-jar",
                mcpServerJar.getAbsolutePath()
            );

            pb.directory(serverHome);
            pb.environment().put("MCREATOR_WORKSPACE", event.getMCreator().getWorkspaceFolder().getAbsolutePath());
            pb.environment().put("MCREATOR_IPC_PORT", String.valueOf(ipcPort));

            // Redirect output for debugging
            File logDir = new File(pluginFile.getParent(), "logs");
            logDir.mkdirs();
            pb.redirectOutput(new File(logDir, "mcp-server.log"));
            pb.redirectError(new File(logDir, "mcp-server-error.log"));

            LOG.info("Starting MCP server...");
            LOG.info("Server jar: {}", mcpServerJar.getAbsolutePath());
            LOG.info("Workspace: {}", event.getMCreator().getWorkspaceFolder().getAbsolutePath());
            LOG.info("HTTP port: {}, IPC port: {}", httpPort, ipcPort);

            Process process = pb.start();
            mcpServerProcess.set(process);

            // Start IPC endpoint
            ipcEndpoint.start(ipcPort, event.getMCreator());

            // Monitor server process
            Thread monitorThread = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    LOG.warn("MCP server process exited with code: {}", exitCode);
                    mcpServerProcess.set(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "MCP-Server-Monitor");
            monitorThread.setDaemon(true);
            monitorThread.start();

            LOG.info("MCP server started successfully");
            showInfoDialog("MCP Server Started", 
                "MCP server is running on http://localhost:" + httpPort + "/mcp\n" +
                "Streamable HTTP: http://localhost:" + httpPort + "/mcp\n" +
                "Legacy SSE: http://localhost:" + httpPort + "/mcp/sse\n" +
                "IPC Port: " + ipcPort);

        } catch (IOException e) {
            LOG.error("Failed to start MCP server", e);
            showErrorDialog("MCP Server Startup Failed", 
                "Failed to start MCP server: " + e.getMessage());
        }
    }

    private void stopMCPServer() {
        Process process = mcpServerProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            LOG.info("Stopping MCP server...");
            process.destroy();
            
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOG.warn("MCP server did not stop gracefully, forcing termination");
                    process.destroyForcibly();
                }
                LOG.info("MCP server stopped");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        if (ipcEndpoint != null) {
            ipcEndpoint.stop();
        }
    }

    private void restartMCPServer(MCreatorLoadedEvent event) {
        LOG.info("Restarting MCP server...");
        stopMCPServer();
        
        // Small delay to ensure cleanup
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            startMCPServer(event);
        });
    }

    private void showMCPStatus() {
        Process process = mcpServerProcess.get();
        String status;
        
        if (process != null && process.isAlive()) {
            status = "MCP Server Status: RUNNING\n" +
                    "HTTP Endpoint: http://localhost:" + currentHttpPort + "/mcp\n" +
                    "SSE Endpoint: http://localhost:" + currentHttpPort + "/mcp/sse\n" +
                    "IPC Port: " + currentIpcPort + "\n" +
                    "Process ID: " + process.pid();
        } else {
            status = "MCP Server Status: NOT RUNNING";
        }

        showInfoDialog("MCP Server Status", status);
    }

    private void showErrorDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE));
    }

    private void showInfoDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Find a free port, starting with the preferred port
     * @param preferredPort The port to try first
     * @return A free port number
     */
    private static int findFreePort(int preferredPort) {
        // Try preferred port first
        try (ServerSocket s = new ServerSocket(preferredPort)) {
            return preferredPort;
        } catch (IOException ignored) {
            // Preferred port is busy, find any free port
        }
        
        // Find any free port
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No free port available", e);
        }
    }
}
