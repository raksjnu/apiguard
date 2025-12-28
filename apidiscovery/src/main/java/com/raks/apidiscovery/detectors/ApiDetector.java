package com.raks.apidiscovery.detectors;

import com.raks.apidiscovery.model.DiscoveryReport;
import java.io.File;

public interface ApiDetector {
    /**
     * Scans the given repository root directory and updates the report.
     * @param repoRoot The root directory of the repository/project.
     * @param report The report object to update with findings.
     * @return true if this detector identified the technology type specific to it.
     */
    boolean scan(File repoRoot, DiscoveryReport report);
}
