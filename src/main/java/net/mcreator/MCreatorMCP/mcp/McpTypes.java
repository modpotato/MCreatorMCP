package net.mcreator.MCreatorMCP.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * MCP protocol data types according to the specification.
 * These classes represent the core MCP concepts of Resources, Tools, and Prompts.
 */
public class McpTypes {

    /**
     * Server capabilities that define what features the server supports
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServerCapabilities {
        @JsonProperty("resources")
        private ResourceCapabilities resources;
        
        @JsonProperty("tools")
        private ToolCapabilities tools;
        
        @JsonProperty("prompts")
        private PromptCapabilities prompts;

        public ServerCapabilities() {}

        public ResourceCapabilities getResources() { return resources; }
        public void setResources(ResourceCapabilities resources) { this.resources = resources; }

        public ToolCapabilities getTools() { return tools; }
        public void setTools(ToolCapabilities tools) { this.tools = tools; }

        public PromptCapabilities getPrompts() { return prompts; }
        public void setPrompts(PromptCapabilities prompts) { this.prompts = prompts; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceCapabilities {
        @JsonProperty("subscribe")
        private Boolean subscribe;
        
        @JsonProperty("listChanged")
        private Boolean listChanged;

        public ResourceCapabilities() {}

        public Boolean getSubscribe() { return subscribe; }
        public void setSubscribe(Boolean subscribe) { this.subscribe = subscribe; }

        public Boolean getListChanged() { return listChanged; }
        public void setListChanged(Boolean listChanged) { this.listChanged = listChanged; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCapabilities {
        @JsonProperty("listChanged")
        private Boolean listChanged;

        public ToolCapabilities() {}

        public Boolean getListChanged() { return listChanged; }
        public void setListChanged(Boolean listChanged) { this.listChanged = listChanged; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PromptCapabilities {
        @JsonProperty("listChanged")
        private Boolean listChanged;

        public PromptCapabilities() {}

        public Boolean getListChanged() { return listChanged; }
        public void setListChanged(Boolean listChanged) { this.listChanged = listChanged; }
    }

    /**
     * Client capabilities that define what features the client supports
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientCapabilities {
        @JsonProperty("sampling")
        private SamplingCapabilities sampling;
        
        @JsonProperty("roots")
        private RootCapabilities roots;

        public ClientCapabilities() {}

        public SamplingCapabilities getSampling() { return sampling; }
        public void setSampling(SamplingCapabilities sampling) { this.sampling = sampling; }

        public RootCapabilities getRoots() { return roots; }
        public void setRoots(RootCapabilities roots) { this.roots = roots; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SamplingCapabilities {
        // Client sampling capabilities can be added here as needed
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RootCapabilities {
        @JsonProperty("listChanged")
        private Boolean listChanged;

        public RootCapabilities() {}

        public Boolean getListChanged() { return listChanged; }
        public void setListChanged(Boolean listChanged) { this.listChanged = listChanged; }
    }

    /**
     * Tool definition according to MCP specification
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("inputSchema")
        private Map<String, Object> inputSchema;

        public Tool() {}

        public Tool(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Map<String, Object> getInputSchema() { return inputSchema; }
        public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
    }

    /**
     * Resource definition according to MCP specification
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Resource {
        @JsonProperty("uri")
        private String uri;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("mimeType")
        private String mimeType;
        
        @JsonProperty("annotations")
        private Annotations annotations;

        public Resource() {}

        public Resource(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public Annotations getAnnotations() { return annotations; }
        public void setAnnotations(Annotations annotations) { this.annotations = annotations; }
    }

    /**
     * Resource content according to MCP specification
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceContent {
        @JsonProperty("uri")
        private String uri;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("mimeType")
        private String mimeType;
        
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("blob")
        private String blob;

        public ResourceContent() {}

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getBlob() { return blob; }
        public void setBlob(String blob) { this.blob = blob; }
    }

    /**
     * Annotations for resources and other objects
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Annotations {
        @JsonProperty("audience")
        private List<String> audience;
        
        @JsonProperty("priority")
        private Double priority;
        
        @JsonProperty("lastModified")
        private String lastModified;

        public Annotations() {}

        public List<String> getAudience() { return audience; }
        public void setAudience(List<String> audience) { this.audience = audience; }

        public Double getPriority() { return priority; }
        public void setPriority(Double priority) { this.priority = priority; }

        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }
    }

    /**
     * Tool result according to MCP specification
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolResult {
        @JsonProperty("content")
        private List<ToolContent> content;
        
        @JsonProperty("isError")
        private Boolean isError;

        public ToolResult() {}

        public ToolResult(List<ToolContent> content, Boolean isError) {
            this.content = content;
            this.isError = isError;
        }

        public List<ToolContent> getContent() { return content; }
        public void setContent(List<ToolContent> content) { this.content = content; }

        public Boolean getIsError() { return isError; }
        public void setIsError(Boolean isError) { this.isError = isError; }
    }

    /**
     * Tool content according to MCP specification
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolContent {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("text")
        private String text;

        public ToolContent() {}

        public ToolContent(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}