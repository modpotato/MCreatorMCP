package net.mcreator.MCreatorMCP.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standard input/output transport for MCP communication.
 * This is the traditional MCP transport method where messages are exchanged
 * via stdin/stdout using JSON-RPC over stdio.
 */
public class McpStdioTransport {

    private static final Logger LOG = LogManager.getLogger("MCP-STDIO");

    private final McpServer mcpServer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;
    private Thread readerThread;

    public McpStdioTransport(McpServer mcpServer) {
        this.mcpServer = mcpServer;
        this.objectMapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start the stdio transport
     */
    public void start() {
        if (running.get()) {
            LOG.warn("Stdio transport already running");
            return;
        }

        running.set(true);
        
        // Start reader thread to process stdin
        readerThread = new Thread(this::processStdin, "MCP-Stdio-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
        
        LOG.info("MCP stdio transport started");
    }

    /**
     * Stop the stdio transport
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);
        
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
        
        LOG.info("MCP stdio transport stopped");
    }

    /**
     * Process incoming messages from stdin
     */
    private void processStdin() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    processMessage(line);
                } catch (Exception e) {
                    LOG.error("Error processing stdin message: " + line, e);
                    sendErrorToStdout(null, -32603, "Internal error", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.error("Error reading from stdin", e);
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * Process a single JSON-RPC message
     */
    private void processMessage(String messageJson) {
        LOG.debug("Received stdio message: {}", messageJson);
        
        try {
            // Parse JSON-RPC message
            JsonRpcMessage request = objectMapper.readValue(messageJson, JsonRpcMessage.class);
            
            // Process message
            JsonRpcMessage response = mcpServer.processMessage(request);
            
            // Send response to stdout (if not a notification)
            if (response != null) {
                sendResponseToStdout(response);
            }
            
        } catch (JsonProcessingException e) {
            LOG.error("Invalid JSON in stdio message", e);
            sendErrorToStdout(null, -32700, "Parse error", "Invalid JSON: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error processing stdio message", e);
            sendErrorToStdout(null, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Send response to stdout
     */
    private void sendResponseToStdout(JsonRpcMessage response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            
            synchronized (System.out) {
                System.out.println(responseJson);
                System.out.flush();
            }
            
            LOG.debug("Sent stdio response: {}", responseJson);
            
        } catch (JsonProcessingException e) {
            LOG.error("Error serializing response to JSON", e);
        }
    }

    /**
     * Send error response to stdout
     */
    private void sendErrorToStdout(Object id, int code, String message, Object data) {
        JsonRpcMessage.JsonRpcError error = new JsonRpcMessage.JsonRpcError(code, message, data);
        JsonRpcMessage errorResponse = new JsonRpcMessage(id, error);
        sendResponseToStdout(errorResponse);
    }

    /**
     * Send notification to stdout
     */
    public void sendNotification(String method, Object params) {
        try {
            JsonRpcMessage notification = new JsonRpcMessage();
            notification.setMethod(method);
            if (params instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> paramsMap = (java.util.Map<String, Object>) params;
                notification.setParams(paramsMap);
            }
            
            sendResponseToStdout(notification);
            
        } catch (Exception e) {
            LOG.error("Error sending notification: " + method, e);
        }
    }

    /**
     * Check if transport is running
     */
    public boolean isRunning() {
        return running.get();
    }
}