package com.raks.apidiscovery;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class TrafficCorrelator {

    // Matches traffic item against all scanned repositories in history
    public static List<Map<String, String>> correlate(String trafficData, String scanDir) {
        List<Map<String, String>> results = new ArrayList<>();
        File scansDir = new File(scanDir);
        if (!scansDir.exists() || !scansDir.isDirectory()) return results;

        // Parse traffic data (simple splitting by newline for now, assuming list of IPs/Domains)
        String[] trafficItems = trafficData.split("\\r?\\n");
        
        // Load all scan results
        File[] validScans = scansDir.listFiles(File::isDirectory);
        if (validScans == null) return results;

        for (File scan : validScans) {
            File resultFile = new File(scan, "scan_results.json");
            if (!resultFile.exists()) continue;

            try {
                String content = new String(Files.readAllBytes(resultFile.toPath()));
                JSONObject json = new JSONObject(content);
                JSONArray repos = json.optJSONArray("repositories");
                if (repos == null) continue;

                for (int i = 0; i < repos.length(); i++) {
                    JSONObject repo = repos.getJSONObject(i);
                    checkMatch(repo, trafficItems, results, scan.getName());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    private static void checkMatch(JSONObject repo, String[] trafficItems, List<Map<String, String>> results, String scanId) {
        String repoName = repo.optString("name");
        String metadata = repo.optString("metadata", ""); // Assuming metadata field stores raw extracted key-values

        for (String item : trafficItems) {
            item = item.trim();
            if (item.isEmpty()) continue;

            // crude matching: checks if the traffic item (IP/Host) appears in the repo's metadata or config
            // In a real scenario, we'd parse specific fields like 'endpoint', 'cert_cn'
            if (metadata.contains(item) || repo.toString().contains(item)) {
                Map<String, String> match = new HashMap<>();
                match.put("traffic_item", item);
                match.put("matched_repo", repoName);
                match.put("scan_id", scanId);
                match.put("confidence", "High"); // Found in collected metadata
                results.add(match);
            }
        }
    }
}
