package net.mcreator.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the MCP server that provides consistent error responses
 * and comprehensive logging for debugging and monitoring.
 */
@Provider
@ApplicationScoped
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Response toResponse(Exception exception) {
        String errorId = UUID.randomUUID().toString();
        
        // Log the full exception with context
        LOG.error("MCP Server error [" + errorId + "]: " + exception.getMessage(), exception);

        // Create error response
        Map<String, Object> errorResponse = Map.of(
            "success", false,
            "error", exception.getMessage(),
            "errorId", errorId,
            "timestamp", Instant.now().toString(),
            "type", exception.getClass().getSimpleName()
        );

        // Determine HTTP status code based on exception type
        int statusCode = determineStatusCode(exception);

        return Response.status(statusCode)
                .entity(errorResponse)
                .header("Content-Type", "application/json")
                .build();
    }

    private int determineStatusCode(Exception exception) {
        // Map exception types to appropriate HTTP status codes
        if (exception instanceof IllegalArgumentException) {
            return 400; // Bad Request
        } else if (exception instanceof SecurityException) {
            return 403; // Forbidden
        } else if (exception instanceof UnsupportedOperationException) {
            return 501; // Not Implemented
        } else if (exception.getMessage() != null && exception.getMessage().contains("timeout")) {
            return 408; // Request Timeout
        } else {
            return 500; // Internal Server Error
        }
    }
}
