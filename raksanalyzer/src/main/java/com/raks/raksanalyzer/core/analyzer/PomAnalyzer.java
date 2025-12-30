package com.raks.raksanalyzer.core.analyzer;
import com.raks.raksanalyzer.domain.model.PomInfo;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
public class PomAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(PomAnalyzer.class);
    public PomInfo analyze(Path pomFile) {
        logger.info("Analyzing POM file: {}", pomFile);
        PomInfo info = new PomInfo();
        try {
            Document doc = XmlUtils.parseXmlFile(pomFile);
            Element root = doc.getDocumentElement();
            info.setModelVersion(getTagValue(root, "modelVersion"));
            info.setGroupId(getTagValue(root, "groupId"));
            info.setArtifactId(getTagValue(root, "artifactId"));
            info.setVersion(getTagValue(root, "version"));
            info.setName(getTagValue(root, "name"));
            info.setDescription(getTagValue(root, "description"));
            info.setPackaging(getTagValue(root, "packaging"));
            Element parentElement = (Element) root.getElementsByTagName("parent").item(0);
            if (parentElement != null) {
                String parentGroupId = getTagValue(parentElement, "groupId");
                String parentArtifactId = getTagValue(parentElement, "artifactId");
                String parentVersion = getTagValue(parentElement, "version");
                if (parentGroupId != null && parentArtifactId != null) {
                    PomInfo.ParentInfo parentInfo = new PomInfo.ParentInfo(parentGroupId, parentArtifactId, parentVersion);
                    String relativePath = getTagValue(parentElement, "relativePath");
                    if (relativePath != null) {
                        parentInfo.setRelativePath(relativePath);
                    }
                    info.setParent(parentInfo);
                }
            }
            if (info.getGroupId() == null && parentElement != null) {
                info.setGroupId(getTagValue(parentElement, "groupId"));
            }
            if (info.getVersion() == null && parentElement != null) {
                info.setVersion(getTagValue(parentElement, "version"));
            }
            NodeList propsList = root.getElementsByTagName("properties");
            if (propsList.getLength() > 0) {
                Element propsElement = (Element) propsList.item(0);
                NodeList props = propsElement.getChildNodes();
                Map<String, String> propMap = new HashMap<>();
                for (int i = 0; i < props.getLength(); i++) {
                    Node node = props.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        propMap.put(node.getNodeName(), node.getTextContent());
                    }
                }
                info.setProperties(propMap);
                if (propMap.containsKey("app.runtime")) {
                    info.setAppRuntime(propMap.get("app.runtime"));
                }
            }
            NodeList depsList = root.getElementsByTagName("dependencies");
            if (depsList.getLength() > 0) {
                Element depsElement = (Element) depsList.item(0);
                NodeList dependencies = depsElement.getElementsByTagName("dependency");
                for (int i = 0; i < dependencies.getLength(); i++) {
                    Element dep = (Element) dependencies.item(i);
                    String g = getTagValue(dep, "groupId");
                    String a = getTagValue(dep, "artifactId");
                    String v = getTagValue(dep, "version");
                    String c = getTagValue(dep, "classifier");
                    if (g != null && a != null) {
                        PomInfo.DependencyInfo depInfo = new PomInfo.DependencyInfo(g, a, v);
                        if (c != null) depInfo.setClassifier(c);
                        info.getDependencies().add(depInfo);
                    }
                }
            }
            NodeList buildList = root.getElementsByTagName("build");
            if (buildList.getLength() > 0) {
                Element buildElement = (Element) buildList.item(0);
                NodeList pluginsList = buildElement.getElementsByTagName("plugins");
                if (pluginsList.getLength() > 0) {
                    Element pluginsElement = (Element) pluginsList.item(0);
                    NodeList plugins = pluginsElement.getElementsByTagName("plugin");
                    for (int i = 0; i < plugins.getLength(); i++) {
                        Element plugin = (Element) plugins.item(i);
                        String g = getTagValue(plugin, "groupId");
                        String a = getTagValue(plugin, "artifactId");
                        String v = getTagValue(plugin, "version");
                        if (g != null && a != null) {
                            info.getPlugins().add(new PomInfo.PluginInfo(g, a, v));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing POM file", e);
        }
        return info;
    }
    private String getTagValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if (node.getParentNode() == parent) {
                    return node.getTextContent();
                }
            }
        }
        return null;
    }
}
