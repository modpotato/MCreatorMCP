package net.mcreator.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkiverse.mcp.server.Resource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that provides MCP resources for workspace context, project files,
 * and other information that can be used by LLM clients.
 */
@ApplicationScoped
@RegisterForReflection
public class WorkspaceResourceService {

    private static final Logger LOG = Logger.getLogger(WorkspaceResourceService.class);

    @ConfigProperty(name = "mcreator.workspace.path", defaultValue = "")
    String workspacePath;

    @Inject
    IPCBridgeService ipcBridge;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===============================
    // WORKSPACE OVERVIEW RESOURCES
    // ===============================

    @Resource(uri = "workspace://overview", name = "Workspace Overview", 
             description = "Complete overview of the MCreator workspace including metadata, settings, and statistics")
    public String getWorkspaceOverview() {
        LOG.debug("Providing workspace overview resource");
        
        try {
            Map<String, Object> overview = new HashMap<>();
            overview.put("timestamp", Instant.now().toString());
            
            if (workspacePath.isEmpty()) {
                overview.put("error", "No workspace path configured");
                return objectMapper.writeValueAsString(overview);
            }

            Path workspaceDir = Paths.get(workspacePath);
            overview.put("path", workspaceDir.toAbsolutePath().toString());
            overview.put("exists", Files.exists(workspaceDir));

            if (!Files.exists(workspaceDir)) {
                overview.put("error", "Workspace directory does not exist");
                return objectMapper.writeValueAsString(overview);
            }

            // Basic workspace information
            overview.put("name", workspaceDir.getFileName().toString());
            overview.put("lastModified", Files.getLastModifiedTime(workspaceDir).toString());

            // Read workspace metadata if available
            Path metadataFile = workspaceDir.resolve(".mcreator").resolve("workspace.mcmeta");
            if (Files.exists(metadataFile)) {
                try {
                    String content = Files.readString(metadataFile);
                    JsonNode metadata = objectMapper.readTree(content);
                    overview.put("metadata", metadata);
                } catch (Exception e) {
                    LOG.warn("Failed to read workspace metadata", e);
                    overview.put("metadataError", e.getMessage());
                }
            }

            // Get live workspace statistics via IPC
            try {
                Map<String, Object> stats = ipcBridge.sendCommand(Map.of("action", "getWorkspaceStats"));
                if (!stats.containsKey("error")) {
                    overview.put("liveStats", stats);
                }
            } catch (Exception e) {
                LOG.debug("Failed to get live workspace stats: " + e.getMessage());
            }

            // File system analysis
            overview.put("fileSystemStats", analyzeWorkspaceFileSystem(workspaceDir));

            return objectMapper.writeValueAsString(overview);

        } catch (Exception e) {
            LOG.error("Failed to create workspace overview", e);
            return createErrorResource("Failed to create workspace overview: " + e.getMessage());
        }
    }

    @Resource(uri = "workspace://elements", name = "Mod Elements", 
             description = "List of all mod elements in the workspace with their types and properties")
    public String getModElements() {
        LOG.debug("Providing mod elements resource");
        
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", Instant.now().toString());

            // Get elements via IPC
            Map<String, Object> response = ipcBridge.sendCommand(Map.of("action", "listModElements"));
            
            if (response.containsKey("error")) {
                result.put("error", response.get("error"));
                result.put("elements", List.of());
            } else {
                result.putAll(response);
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            LOG.error("Failed to get mod elements", e);
            return createErrorResource("Failed to get mod elements: " + e.getMessage());
        }
    }

    @Resource(uri = "workspace://variables", name = "Workspace Variables", 
             description = "All variables defined in the workspace including their types and scopes")
    public String getWorkspaceVariables() {
        LOG.debug("Providing workspace variables resource");
        
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", Instant.now().toString());

            // Get variables via IPC
            Map<String, Object> response = ipcBridge.sendCommand(Map.of("action", "listVariables"));
            
            if (response.containsKey("error")) {
                result.put("error", response.get("error"));
                result.put("variables", List.of());
            } else {
                result.putAll(response);
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            LOG.error("Failed to get workspace variables", e);
            return createErrorResource("Failed to get workspace variables: " + e.getMessage());
        }
    }

    // ===============================
    // PROJECT STRUCTURE RESOURCES
    // ===============================

    @Resource(uri = "workspace://structure", name = "Project Structure", 
             description = "Directory structure and file organization of the workspace")
    public String getProjectStructure() {
        LOG.debug("Providing project structure resource");
        
        try {
            if (workspacePath.isEmpty()) {
                return createErrorResource("No workspace path configured");
            }

            Path workspaceDir = Paths.get(workspacePath);
            if (!Files.exists(workspaceDir)) {
                return createErrorResource("Workspace directory does not exist");
            }

            Map<String, Object> structure = new HashMap<>();
            structure.put("timestamp", Instant.now().toString());
            structure.put("rootPath", workspaceDir.toAbsolutePath().toString());
            structure.put("structure", buildDirectoryTree(workspaceDir, 3)); // Max depth 3

            return objectMapper.writeValueAsString(structure);

        } catch (Exception e) {
            LOG.error("Failed to get project structure", e);
            return createErrorResource("Failed to get project structure: " + e.getMessage());
        }
    }

    @Resource(uri = "workspace://resources", name = "Workspace Resources", 
             description = "All resource files including textures, sounds, models, and structures")
    public String getWorkspaceResources() {
        LOG.debug("Providing workspace resources");
        
        try {
            Map<String, Object> resources = new HashMap<>();
            resources.put("timestamp", Instant.now().toString());

            if (workspacePath.isEmpty()) {
                resources.put("error", "No workspace path configured");
                return objectMapper.writeValueAsString(resources);
            }

            Path workspaceDir = Paths.get(workspacePath);
            if (!Files.exists(workspaceDir)) {
                resources.put("error", "Workspace directory does not exist");
                return objectMapper.writeValueAsString(resources);
            }

            // Scan for different types of resources
            resources.put("textures", findResourceFiles(workspaceDir, "textures", List.of(".png", ".jpg", ".jpeg", ".gif")));
            resources.put("models", findResourceFiles(workspaceDir, "models", List.of(".json")));
            resources.put("sounds", findResourceFiles(workspaceDir, "sounds", List.of(".ogg", ".wav", ".mp3")));
            resources.put("structures", findResourceFiles(workspaceDir, "structures", List.of(".nbt")));
            resources.put("data", findResourceFiles(workspaceDir, "data", List.of(".json")));

            return objectMapper.writeValueAsString(resources);

        } catch (Exception e) {
            LOG.error("Failed to get workspace resources", e);
            return createErrorResource("Failed to get workspace resources: " + e.getMessage());
        }
    }

    // ===============================
    // CONFIGURATION RESOURCES
    // ===============================

    @Resource(uri = "workspace://settings", name = "Workspace Settings", 
             description = "Workspace configuration and generation settings")
    public String getWorkspaceSettings() {
        LOG.debug("Providing workspace settings resource");
        
        try {
            Map<String, Object> settings = new HashMap<>();
            settings.put("timestamp", Instant.now().toString());

            if (workspacePath.isEmpty()) {
                settings.put("error", "No workspace path configured");
                return objectMapper.writeValueAsString(settings);
            }

            // Try to read workspace settings file
            Path workspaceDir = Paths.get(workspacePath);
            Path settingsFile = workspaceDir.resolve(".mcreator").resolve("workspaceSettings");
            
            if (Files.exists(settingsFile)) {
                try {
                    String content = Files.readString(settingsFile);
                    JsonNode settingsJson = objectMapper.readTree(content);
                    settings.put("settings", settingsJson);
                } catch (Exception e) {
                    LOG.warn("Failed to read workspace settings file", e);
                    settings.put("settingsError", e.getMessage());
                }
            }

            // Get additional settings via IPC
            try {
                Map<String, Object> response = ipcBridge.sendCommand(Map.of("action", "getWorkspaceSettings"));
                if (!response.containsKey("error")) {
                    settings.put("liveSettings", response);
                }
            } catch (Exception e) {
                LOG.debug("Failed to get live workspace settings: " + e.getMessage());
            }

            return objectMapper.writeValueAsString(settings);

        } catch (Exception e) {
            LOG.error("Failed to get workspace settings", e);
            return createErrorResource("Failed to get workspace settings: " + e.getMessage());
        }
    }

    @Resource(uri = "workspace://build-info", name = "Build Information", 
             description = "Information about the workspace build configuration and Gradle setup")
    public String getBuildInfo() {
        LOG.debug("Providing build information resource");
        
        try {
            Map<String, Object> buildInfo = new HashMap<>();
            buildInfo.put("timestamp", Instant.now().toString());

            if (workspacePath.isEmpty()) {
                buildInfo.put("error", "No workspace path configured");
                return objectMapper.writeValueAsString(buildInfo);
            }

            Path workspaceDir = Paths.get(workspacePath);
            
            // Check for build files
            Path buildGradle = workspaceDir.resolve("build.gradle");
            Path gradleProperties = workspaceDir.resolve("gradle.properties");
            Path settingsGradle = workspaceDir.resolve("settings.gradle");

            buildInfo.put("hasBuildGradle", Files.exists(buildGradle));
            buildInfo.put("hasGradleProperties", Files.exists(gradleProperties));
            buildInfo.put("hasSettingsGradle", Files.exists(settingsGradle));

            // Read gradle.properties if available
            if (Files.exists(gradleProperties)) {
                try {
                    Properties props = new Properties();
                    props.load(Files.newBufferedReader(gradleProperties));
                    buildInfo.put("gradleProperties", props);
                } catch (Exception e) {
                    LOG.warn("Failed to read gradle.properties", e);
                }
            }

            return objectMapper.writeValueAsString(buildInfo);

        } catch (Exception e) {
            LOG.error("Failed to get build info", e);
            return createErrorResource("Failed to get build info: " + e.getMessage());
        }
    }

    // ===============================
    // UTILITY METHODS
    // ===============================

    private Map<String, Object> analyzeWorkspaceFileSystem(Path workspaceDir) throws IOException {
        Map<String, Object> stats = new HashMap<>();
        
        List<Path> allFiles = Files.walk(workspaceDir)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        stats.put("totalFiles", allFiles.size());
        
        // Count by extension
        Map<String, Long> extensionCounts = allFiles.stream()
                .map(path -> {
                    String fileName = path.getFileName().toString();
                    int lastDot = fileName.lastIndexOf('.');
                    return lastDot > 0 ? fileName.substring(lastDot) : "no-extension";
                })
                .collect(Collectors.groupingBy(ext -> ext, Collectors.counting()));
        
        stats.put("filesByExtension", extensionCounts);
        
        // Directory counts
        long dirCount = Files.walk(workspaceDir)
                .filter(Files::isDirectory)
                .count() - 1; // Exclude root directory
        stats.put("totalDirectories", dirCount);

        return stats;
    }

    private Map<String, Object> buildDirectoryTree(Path root, int maxDepth) throws IOException {
        Map<String, Object> tree = new HashMap<>();
        tree.put("name", root.getFileName().toString());
        tree.put("type", "directory");
        tree.put("path", root.toString());

        if (maxDepth <= 0) {
            return tree;
        }

        List<Map<String, Object>> children = new ArrayList<>();
        
        try {
            Files.list(root)
                    .sorted()
                    .forEach(path -> {
                        try {
                            Map<String, Object> child = new HashMap<>();
                            child.put("name", path.getFileName().toString());
                            child.put("path", path.toString());
                            
                            if (Files.isDirectory(path)) {
                                child.put("type", "directory");
                                if (maxDepth > 1) {
                                    child.putAll(buildDirectoryTree(path, maxDepth - 1));
                                }
                            } else {
                                child.put("type", "file");
                                child.put("size", Files.size(path));
                            }
                            
                            children.add(child);
                        } catch (IOException e) {
                            LOG.warn("Failed to process path: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Failed to list directory: {}", root, e);
        }

        tree.put("children", children);
        return tree;
    }

    private List<String> findResourceFiles(Path workspaceDir, String category, List<String> extensions) {
        List<String> resources = new ArrayList<>();
        
        try {
            Path resourcesDir = workspaceDir.resolve("src").resolve("main").resolve("resources");
            if (!Files.exists(resourcesDir)) {
                return resources;
            }

            Files.walk(resourcesDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return extensions.stream().anyMatch(fileName::endsWith);
                    })
                    .forEach(path -> {
                        String relativePath = resourcesDir.relativize(path).toString();
                        resources.add(relativePath.replace('\\', '/'));
                    });

        } catch (IOException e) {
            LOG.warn("Failed to find {} resources", category, e);
        }

        return resources;
    }

    private String createErrorResource(String message) {
        try {
            Map<String, Object> error = Map.of(
                "error", message,
                "timestamp", Instant.now().toString()
            );
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            LOG.error("Failed to create error resource", e);
            return "{\"error\":\"Failed to create error response\"}";
        }
    }
}
