# MuleGuard Trial Protection - Quick Reference

## ✅ What's Protected

### 1. Source Code Protection
- JAR contains compiled bytecode (`.class` files), not source `.java` files
- Can be decompiled but requires technical knowledge
- For stronger protection, use ProGuard obfuscation (see TRIAL_VERSION_README.md)

### 2. Trial Restrictions
- **Expiry Date**: Application stops working after configured date
- **Usage Limit**: Maximum number of executions (default: 10 runs)
- **Tracking File**: `~/.muleguard-trial.dat` in user's home directory

## 🚀 Quick Start

### Build Trial Version
1. Ensure `TRIAL_MODE = true` in `TrialLicenseManager.java` (line 20)
2. Set expiry date (line 23): `EXPIRY_DATE = "2025-02-01"`
3. Set execution limit (line 24): `MAX_EXECUTIONS = 10`
4. Build: `mvn clean package`
5. Distribute: `target/muleguard-1.0.0-jar-with-raks.jar`

### Build Full Version
1. Change `TRIAL_MODE = false` in `TrialLicenseManager.java` (line 20)
2. Build: `mvn clean package`
3. Distribute: `target/muleguard-1.0.0-jar-with-raks.jar`

## 📝 Configuration Files

### TrialLicenseManager.java
```
Location: src/main/java/com/raks/muleguard/license/TrialLicenseManager.java

Line 20: TRIAL_MODE = true/false    ← Enable/Disable trial
Line 23: EXPIRY_DATE = "2025-02-01" ← Set expiry date
Line 24: MAX_EXECUTIONS = 10        ← Set usage limit
```

### MuleGuardMain.java
```
Location: src/main/java/com/raks/muleguard/MuleGuardMain.java

Lines 31-45: Trial license check code
```

## 🔒 Security Level

| Protection Type | Effectiveness | Notes |
|----------------|---------------|-------|
| Compiled bytecode | ⭐⭐ Basic | Can be decompiled with tools |
| Trial expiry date | ⭐⭐⭐ Good | Hard-coded in code |
| Usage counter | ⭐⭐⭐ Good | Stored in hidden file |
| ProGuard obfuscation | ⭐⭐⭐⭐ Strong | Makes decompilation very difficult |

## ⚠️ Important Notes

1. **Not Foolproof**: Determined users with technical skills can bypass these protections
2. **Purpose**: Designed to prevent casual misuse and unauthorized distribution
3. **Best Practice**: Use for evaluation/trial purposes, not as primary security
4. **Recommendation**: For commercial use, implement server-side license validation

## 📧 Contact

For custom licensing or questions: raksjnu@gmail.com
