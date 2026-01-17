# Aegis Version Comparison & Enhanced Reporting

## Quick Reference

### Version Comparison Operators

```yaml
# Parent POM
parent:
  groupId: com.truist.eapi
  artifactId: MuleParentPom
  minVersion: 3.0.0  # >= 3.0.0

# Properties
properties:
- name: mule.maven.plugin.version
  minVersion: 4.5.0  # >= 4.5.0
  maxVersion: 5.0.0  # <= 5.0.0
- name: app.runtime
  minVersion: 4.9.0  # Works with 4.9.0, 4.9.LTS, etc.
```

### Alphanumeric Version Handling

**How `4.9.LTS` is compared**:
```
Version: "4.9.LTS"
  major: 4
  minor: 9
  patch: 0
  qualifier: "LTS"

Comparison: 4.9.LTS >= 4.9.0 → TRUE (LTS > no qualifier)
```

### Enhanced Reporting

Reports now show actual values found:
```
Actual Values Found:
• Parent: com.truist.eapi:MuleParentPom:3.0.5
• Property 'app.runtime': 4.9.LTS
```

---

## See Also

- [POM Version Comparison Guide](pom-version-comparison.md)
- [Enhanced Reporting Guide](enhanced-reporting.md)
- [Sample Rules](../sample-rules.yaml)
