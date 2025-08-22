package net.mcreator.MCreatorMCP.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a JSON-RPC 2.0 message according to the MCP specification.
 * This is the base message format used for all MCP communication.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcMessage {
    
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    @JsonProperty("id")
    private Object id;
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("params")
    private Map<String, Object> params;
    
    @JsonProperty("result")
    private Object result;
    
    @JsonProperty("error")
    private JsonRpcError error;

    public JsonRpcMessage() {}

    public JsonRpcMessage(String method, Map<String, Object> params) {
        this.method = method;
        this.params = params;
    }

    public JsonRpcMessage(Object id, Object result) {
        this.id = id;
        this.result = result;
    }

    public JsonRpcMessage(Object id, JsonRpcError error) {
        this.id = id;
        this.error = error;
    }

    // Getters and setters
    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }

    public Object getId() { return id; }
    public void setId(Object id) { this.id = id; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }

    public JsonRpcError getError() { return error; }
    public void setError(JsonRpcError error) { this.error = error; }

    public boolean isRequest() {
        return method != null && error == null && result == null;
    }

    public boolean isResponse() {
        return method == null && (result != null || error != null);
    }

    public boolean isNotification() {
        return method != null && id == null;
    }

    public static class JsonRpcError {
        @JsonProperty("code")
        private int code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("data")
        private Object data;

        public JsonRpcError() {}

        public JsonRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public JsonRpcError(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }
}