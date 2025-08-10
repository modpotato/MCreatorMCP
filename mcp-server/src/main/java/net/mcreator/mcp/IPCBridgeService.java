package net.mcreator.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IPC Bridge Service for communication between the MCP server and MCreator plugin.
 * Uses HTTP-based communication for reliable request/response patterns.
 */
@ApplicationScoped
@RegisterForReflection
public class IPCBridgeService {

    private static final Logger LOG = Logger.getLogger(IPCBridgeService.class);
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 500;
    
    @ConfigProperty(name = "mcreator.ipc.enabled", defaultValue = "true")
    boolean ipcEnabled;
    
    @ConfigProperty(name = "mcreator.ipc.timeout", defaultValue = "30")
    int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong(0);
    
    private ExecutorService executorService;
    private String ipcEndpoint;
    private int ipcPort;

    /**
     * Initialize the IPC bridge
     * @param port The port to connect to for IPC communication
     */
    public void initialize(int port) {
        if (!ipcEnabled) {
            LOG.info("IPC Bridge is disabled");
            return;
        }

        this.ipcPort = port;
        this.ipcEndpoint = "http://localhost:" + port + "/mcp-ipc";
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "IPC-Bridge-" + requestCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        initialized.set(true);
        LOG.info("IPC Bridge initialized - endpoint: " + ipcEndpoint);
        
        // Test connection
        testConnection();
    }

    /**
     * Send a command to MCreator via IPC
     * @param command The command to send
     * @return The response from MCreator
     * @throws RuntimeException if IPC is not enabled or communication fails
     */
    public Map<String, Object> sendCommand(Map<String, Object> command) {
        if (!ipcEnabled) {
            throw new RuntimeException("IPC is not enabled");
        }

        if (!initialized.get()) {
            throw new RuntimeException("IPC Bridge not initialized");
        }

        long requestId = requestCounter.incrementAndGet();
        LOG.debug("Sending IPC command " + requestId + ": " + command.get("action"));

        return executeWithRetry(() -> sendCommandInternal(command, requestId));
    }

    /**
     * Send a command asynchronously
     * @param command The command to send
     * @return CompletableFuture with the response
     */
    public CompletableFuture<Map<String, Object>> sendCommandAsync(Map<String, Object> command) {
        if (!ipcEnabled) {
            return CompletableFuture.failedFuture(new RuntimeException("IPC is not enabled"));
        }

        return CompletableFuture.supplyAsync(() -> sendCommand(command), executorService);
    }

    /**
     * Check if the IPC bridge is available and can communicate with MCreator
     * @return true if the bridge is available
     */
    public boolean isAvailable() {
        if (!ipcEnabled || !initialized.get()) {
            return false;
        }

        try {
            Map<String, Object> response = sendCommandInternal(Map.of("action", "ping"), -1);
            return response.containsKey("pong") || !response.containsKey("error");
        } catch (Exception e) {
            LOG.debug("IPC availability check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get connection statistics
     * @return Map with connection statistics
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "enabled", ipcEnabled,
            "initialized", initialized.get(),
            "endpoint", ipcEndpoint != null ? ipcEndpoint : "not-set",
            "requestCount", requestCounter.get(),
            "available", isAvailable()
        );
    }

    private Map<String, Object> sendCommandInternal(Map<String, Object> command, long requestId) {
        try {
            // Add metadata to command
            Map<String, Object> commandWithMeta = new HashMap<>(command);
            commandWithMeta.put("requestId", requestId);
            commandWithMeta.put("timestamp", Instant.now().toEpochMilli());
            commandWithMeta.put("source", "mcp-server");

            // Serialize command
            String requestBody = objectMapper.writeValueAsString(commandWithMeta);
            
            // Create HTTP connection
            URL url = new URL(ipcEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MCreator-MCP-Server/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            String responseBody;
            
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseBody = response.toString();
                }
            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseBody = response.toString();
                }
                
                LOG.warn("IPC request " + requestId + " failed with status " + responseCode + ": " + responseBody);
                return Map.of("error", "HTTP " + responseCode + ": " + responseBody);
            }

            // Parse response
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            
            LOG.debug("IPC command " + requestId + " completed successfully");
            return responseMap;

        } catch (ConnectException e) {
            LOG.warn("IPC connection failed - MCreator may not be running or IPC endpoint not available");
            return Map.of("error", "Connection failed: MCreator IPC not available");
        } catch (SocketTimeoutException e) {
            LOG.warn("IPC request " + requestId + " timed out after " + timeoutSeconds + " seconds");
            return Map.of("error", "Request timed out after " + timeoutSeconds + " seconds");
        } catch (Exception e) {
            LOG.error("IPC command {} failed", requestId, e);
            return Map.of("error", "IPC communication failed: " + e.getMessage());
        }
    }

    private <T> T executeWithRetry(Callable<T> operation) {
        Exception lastException = null;
        int delay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < MAX_RETRIES) {
                    LOG.debug("IPC attempt " + attempt + " failed, retrying in " + delay + "ms: " + e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    delay *= 2; // Exponential backoff
                }
            }
        }

        throw new RuntimeException("IPC operation failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private void testConnection() {
        if (!ipcEnabled) return;

        executorService.submit(() -> {
            try {
                // Wait a bit for MCreator to start up
                Thread.sleep(2000);
                
                if (isAvailable()) {
                    LOG.info("IPC Bridge connection test successful");
                } else {
                    LOG.warn("IPC Bridge connection test failed - MCreator may not be running");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("IPC Bridge connection test failed", e);
            }
        });
    }

    void onShutdown(@Observes ShutdownEvent ev) {
        if (executorService != null && !executorService.isShutdown()) {
            LOG.info("Shutting down IPC Bridge...");
            executorService.shutdown();
            
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            LOG.info("IPC Bridge shutdown completed");
        }
    }
}
