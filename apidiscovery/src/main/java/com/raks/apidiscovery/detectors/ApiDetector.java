package com.raks.apidiscovery.detectors;
import com.raks.apidiscovery.model.DiscoveryReport;
import java.io.File;
public interface ApiDetector {
    boolean scan(File repoRoot, DiscoveryReport report);
}
