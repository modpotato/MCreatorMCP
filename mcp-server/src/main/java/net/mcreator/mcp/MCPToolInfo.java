package net.mcreator.mcp;

import java.util.List;
import java.util.Objects;

/**
 * Information about an MCP tool including its name, description, parameters, and metadata.
 */
public class MCPToolInfo {
    private final String name;
    private final String description;
    private final String detailedDescription;
    private final List<MCPToolParameter> parameters;
    private final String category;

    public MCPToolInfo(String name, String description, String detailedDescription, 
                       List<MCPToolParameter> parameters, String category) {
        this.name = Objects.requireNonNull(name, "Tool name cannot be null");
        this.description = Objects.requireNonNull(description, "Tool description cannot be null");
        this.detailedDescription = detailedDescription;
        this.parameters = parameters != null ? List.copyOf(parameters) : List.of();
        this.category = category != null ? category : "general";
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDetailedDescription() {
        return detailedDescription != null ? detailedDescription : description;
    }

    public List<MCPToolParameter> getParameters() {
        return parameters;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPToolInfo that = (MCPToolInfo) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "MCPToolInfo{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", parameters=" + parameters.size() +
                '}';
    }
}
