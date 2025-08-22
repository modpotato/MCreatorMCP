package net.mcreator.MCreatorMCP.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTP transport for MCP communication.
 * Supports both regular HTTP requests and Server-Sent Events (SSE) for compatibility
 * with different MCP client implementations.
 */
public class McpHttpTransport {

    private static final Logger LOG = LogManager.getLogger("MCP-HTTP");

    private final McpServer mcpServer;
    private final ObjectMapper objectMapper;
    private HttpServer httpServer;
    private final int port;

    public McpHttpTransport(McpServer mcpServer, int port) {
        this.mcpServer = mcpServer;
        this.port = port;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Start the HTTP server
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        
        // Standard MCP HTTP endpoint
        httpServer.createContext("/mcp", new McpHttpHandler());
        
        // Server-Sent Events endpoint for legacy compatibility
        httpServer.createContext("/mcp/sse", new McpSseHandler());
        
        // Health check endpoint
        httpServer.createContext("/health", new HealthHandler());
        
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        
        LOG.info("MCP HTTP transport started on port {} with endpoints:", port);
        LOG.info("  - Standard HTTP: http://localhost:{}/mcp", port);
        LOG.info("  - SSE (legacy): http://localhost:{}/mcp/sse", port);
        LOG.info("  - Health check: http://localhost:{}/health", port);
    }

    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            LOG.info("MCP HTTP transport stopped");
        }
    }

    /**
     * HTTP handler for standard MCP requests
     */
    private class McpHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                // Read request body
                String requestBody = readRequestBody(exchange);
                LOG.debug("Received MCP request: {}", requestBody);

                // Parse JSON-RPC message
                JsonRpcMessage request = objectMapper.readValue(requestBody, JsonRpcMessage.class);

                // Process message
                JsonRpcMessage response = mcpServer.processMessage(request);

                // Send response (if not a notification)
                if (response != null) {
                    String responseBody = objectMapper.writeValueAsString(response);
                    sendJsonResponse(exchange, 200, responseBody);
                    LOG.debug("Sent MCP response: {}", responseBody);
                } else {
                    // Notification - send empty response
                    exchange.sendResponseHeaders(204, -1);
                }

            } catch (JsonProcessingException e) {
                LOG.error("Invalid JSON in request", e);
                sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            } catch (Exception e) {
                LOG.error("Error processing MCP request", e);
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }

    /**
     * SSE handler for legacy MCP clients that expect Server-Sent Events
     */
    private class McpSseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add SSE headers
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                // Read request body
                String requestBody = readRequestBody(exchange);
                LOG.debug("Received MCP SSE request: {}", requestBody);

                // Parse JSON-RPC message
                JsonRpcMessage request = objectMapper.readValue(requestBody, JsonRpcMessage.class);

                // Process message
                JsonRpcMessage response = mcpServer.processMessage(request);

                // Send SSE response
                exchange.sendResponseHeaders(200, 0);
                
                if (response != null) {
                    try (OutputStreamWriter writer = new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8)) {
                        String responseBody = objectMapper.writeValueAsString(response);
                        writer.write("data: " + responseBody + "\n\n");
                        writer.flush();
                        LOG.debug("Sent MCP SSE response: {}", responseBody);
                    }
                }

            } catch (JsonProcessingException e) {
                LOG.error("Invalid JSON in SSE request", e);
                sendSseError(exchange, "Invalid JSON: " + e.getMessage());
            } catch (Exception e) {
                LOG.error("Error processing MCP SSE request", e);
                sendSseError(exchange, "Internal server error: " + e.getMessage());
            }
        }

        private void sendSseError(HttpExchange exchange, String error) throws IOException {
            JsonRpcMessage.JsonRpcError jsonRpcError = new JsonRpcMessage.JsonRpcError(-32603, error);
            JsonRpcMessage errorResponse = new JsonRpcMessage(null, jsonRpcError);
            
            exchange.sendResponseHeaders(200, 0);
            try (OutputStreamWriter writer = new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8)) {
                String errorBody = objectMapper.writeValueAsString(errorResponse);
                writer.write("data: " + errorBody + "\n\n");
                writer.flush();
            }
        }
    }

    /**
     * Health check handler
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            String health = "{\n" +
                "  \"status\": \"healthy\",\n" +
                "  \"service\": \"MCreator MCP Server\",\n" +
                "  \"initialized\": " + mcpServer.isInitialized() + ",\n" +
                "  \"workspace\": " + (mcpServer.getWorkspace() != null ? "\"loaded\"" : "null") + "\n" +
                "}";
            
            sendJsonResponse(exchange, 200, health);
        }
    }

    /**
     * Read request body as string
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    /**
     * Send error response
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String error = "{\"error\": \"" + message.replace("\"", "\\\"") + "\"}";
        sendJsonResponse(exchange, statusCode, error);
    }

    public int getPort() {
        return port;
    }
}