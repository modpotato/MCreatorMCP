package net.mcreator.mcp;

import java.util.Objects;

/**
 * Represents a parameter for an MCP tool with type information and metadata.
 */
public class MCPToolParameter {
    private final String name;
    private final String type;
    private final String description;
    private final boolean required;
    private final Object defaultValue;

    public MCPToolParameter(String name, String type, String description, boolean required) {
        this(name, type, description, required, null);
    }

    public MCPToolParameter(String name, String type, String description, boolean required, Object defaultValue) {
        this.name = Objects.requireNonNull(name, "Parameter name cannot be null");
        this.type = Objects.requireNonNull(type, "Parameter type cannot be null");
        this.description = description != null ? description : "";
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPToolParameter that = (MCPToolParameter) o;
        return Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "MCPToolParameter{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                '}';
    }
}
