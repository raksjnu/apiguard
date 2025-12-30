package com.raks.raksanalyzer.core.analyzer;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.ProjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
public class ProjectCrawler {
    private static final Logger logger = LoggerFactory.getLogger(ProjectCrawler.class);
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private final Set<String> excludedFolders;
    public ProjectCrawler() {
        String excludes = config.getProperty("file.exclusion.folders", "target,.git,.mule,.settings,.github");
        this.excludedFolders = Arrays.stream(excludes.split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
    }
    public ProjectNode crawl(Path rootPath) {
        logger.info("Crawling project structure from: {}", rootPath);
        File rootFile = rootPath.toFile();
        return scanNode(rootFile, rootPath);
    }
    private ProjectNode scanNode(File file, Path contextRoot) {
        String relativePath = contextRoot.relativize(file.toPath()).toString();
        ProjectNode.NodeType type = file.isDirectory() ? ProjectNode.NodeType.DIRECTORY : ProjectNode.NodeType.FILE;
        ProjectNode node = new ProjectNode(file.getName(), file.getAbsolutePath(), relativePath, type);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                for (File child : children) {
                    if (isExcluded(child)) {
                        continue;
                    }
                    node.addChild(scanNode(child, contextRoot));
                }
            }
        }
        return node;
    }
    private boolean isExcluded(File file) {
        if (file.isDirectory()) {
            return excludedFolders.contains(file.getName());
        }
        return false;
    }
}
