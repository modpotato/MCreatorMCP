package net.mcreator.MCreatorMCP.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Simple tests for the MCP server implementation
 */
public class McpServerTest {

    private McpServer mcpServer;

    @BeforeEach
    public void setUp() {
        mcpServer = new McpServer("Test MCP Server", "1.0.0");
    }

    @Test
    public void testInitialization() {
        assertFalse(mcpServer.isInitialized(), "Server should not be initialized initially");
        
        // Test initialize request
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> capabilities = new HashMap<>();
        params.put("protocolVersion", "2025-06-18");
        params.put("capabilities", capabilities);
        
        JsonRpcMessage initRequest = new JsonRpcMessage("initialize", params);
        initRequest.setId(1);
        
        JsonRpcMessage response = mcpServer.processMessage(initRequest);
        
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getId(), "Response ID should match request ID");
        assertNull(response.getError(), "Response should not have error");
        assertNotNull(response.getResult(), "Response should have result");
        
        assertTrue(mcpServer.isInitialized(), "Server should be initialized after initialize request");
    }

    @Test
    public void testToolsList() {
        // Initialize server first
        Map<String, Object> initParams = new HashMap<>();
        initParams.put("protocolVersion", "2025-06-18");
        initParams.put("capabilities", new HashMap<>());
        
        JsonRpcMessage initRequest = new JsonRpcMessage("initialize", initParams);
        initRequest.setId(1);
        mcpServer.processMessage(initRequest);
        
        // Test tools/list request
        JsonRpcMessage toolsRequest = new JsonRpcMessage("tools/list", new HashMap<>());
        toolsRequest.setId(2);
        
        JsonRpcMessage response = mcpServer.processMessage(toolsRequest);
        
        assertNotNull(response, "Response should not be null");
        assertEquals(2, response.getId(), "Response ID should match request ID");
        assertNull(response.getError(), "Response should not have error");
        assertNotNull(response.getResult(), "Response should have result");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("tools"), "Result should contain tools");
    }

    @Test
    public void testResourcesList() {
        // Initialize server first
        Map<String, Object> initParams = new HashMap<>();
        initParams.put("protocolVersion", "2025-06-18");
        initParams.put("capabilities", new HashMap<>());
        
        JsonRpcMessage initRequest = new JsonRpcMessage("initialize", initParams);
        initRequest.setId(1);
        mcpServer.processMessage(initRequest);
        
        // Test resources/list request
        JsonRpcMessage resourcesRequest = new JsonRpcMessage("resources/list", new HashMap<>());
        resourcesRequest.setId(3);
        
        JsonRpcMessage response = mcpServer.processMessage(resourcesRequest);
        
        assertNotNull(response, "Response should not be null");
        assertEquals(3, response.getId(), "Response ID should match request ID");
        assertNull(response.getError(), "Response should not have error");
        assertNotNull(response.getResult(), "Response should have result");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("resources"), "Result should contain resources");
    }

    @Test
    public void testInvalidMethod() {
        JsonRpcMessage invalidRequest = new JsonRpcMessage("invalid/method", new HashMap<>());
        invalidRequest.setId(4);
        
        JsonRpcMessage response = mcpServer.processMessage(invalidRequest);
        
        assertNotNull(response, "Response should not be null");
        assertEquals(4, response.getId(), "Response ID should match request ID");
        assertNotNull(response.getError(), "Response should have error");
        assertEquals(-32601, response.getError().getCode(), "Error code should be method not found");
    }
}