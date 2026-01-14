# ApiGuard Common Information

This file serves as the single source of truth for common contact details, credentials, and configuration references used across the ApiGuard ecosystem (MuleGuard, RaksAnalyzer, etc.).

## 📧 Contact Information

For further information, support, or questions, please reach out to the core team:

- **Name**: Rakesh Kumar (Raks)
- **Email**: [Rakesh.kumar@ibm.com](mailto:Rakesh.kumar@ibm.com)
- **Role**: IBM Application Architect

---

## 🔐 Repository Credentials

> **⚠️ SECURITY WARNING**: This file contains sensitive access tokens. Ensure this file is NOT committed to public repositories or shared improperly. Add this file to `.gitignore` if necessary.

### GitLab Configuration
Used for `gitanalyzer` and repository fetching.

```properties
gitlab.url=https://gitlab.com
gitlab.group=https://gitlab.com/raks-group
gitlab.token=[REDACTED_FOR_SECURITY_USE_LOCAL_CONFIG]
```

### GitHub Configuration
Used for specific GitHub integrations.

```properties
github.url=https://api.github.com
github.owner=raksjnu
github.token=[REDACTED_FOR_SECURITY_USE_LOCAL_CONFIG]
```
