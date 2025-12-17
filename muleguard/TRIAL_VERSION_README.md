# MuleGuard Trial Version Configuration

## Overview
This document explains how to build and distribute trial versions of MuleGuard with built-in protection.

## Trial Features

### 1. Expiry Date Protection
- Set in `TrialLicenseManager.java` line 16: `EXPIRY_DATE = "2025-02-01"`
- Application will stop working after this date
- Users will see a clear message with contact information

### 2. Execution Limit
- Set in `TrialLicenseManager.java` line 17: `MAX_EXECUTIONS = 10`
- Limits the number of times the tool can be run
- Counter is stored in user's home directory: `~/.muleguard-trial.dat`

### 3. Source Code Protection
The JAR file contains compiled bytecode, not source code. However:
- **Basic Protection**: Compiled `.class` files can be decompiled
- **Enhanced Protection**: Use ProGuard obfuscation (see below)

## Building Different Versions

### Quick Toggle: TRIAL_MODE Flag

The easiest way to switch between trial and full versions is using the `TRIAL_MODE` flag in `TrialLicenseManager.java`:

**Trial Version (Default):**
```java
private static final boolean TRIAL_MODE = true;  // Trial restrictions enabled
```

**Full Version:**
```java
private static final boolean TRIAL_MODE = false; // No restrictions
```

Then rebuild:
```bash
mvn clean package
```

### Trial Version (Current Setup)
```bash
# Ensure TRIAL_MODE = true in TrialLicenseManager.java
mvn clean package
```

The trial license check is active in `MuleGuardMain.java` lines 31-45.

### Full Version (No Restrictions)

**Recommended Method - Use TRIAL_MODE flag:**
1. Edit `src/main/java/com/raks/muleguard/license/TrialLicenseManager.java`
2. Change line 20: `private static final boolean TRIAL_MODE = false;`
3. Build: `mvn clean package`

**Alternative Method - Comment out the trial check:**
   Edit `MuleGuardMain.java` and comment out lines 31-45:
   ```java
   // TRIAL LICENSE CHECK - Comment out this block for production/full version
   /*
   try {
       TrialLicenseManager licenseManager = new TrialLicenseManager();
       licenseManager.validateTrial();
   } catch (TrialLicenseManager.LicenseException e) {
       System.err.println("\n╔════════════════════════════════════════════════════════════╗");
       System.err.println("║                  LICENSE ERROR                             ║");
       System.err.println("╠════════════════════════════════════════════════════════════╣");
       System.err.println("║  " + e.getMessage());
       System.err.println("╚════════════════════════════════════════════════════════════╝\n");
       System.exit(1);
   }
   */
   // END TRIAL LICENSE CHECK
   ```

### Customizing Trial Parameters

Edit `TrialLicenseManager.java`:

```java
// Change expiry date (format: YYYY-MM-DD)
private static final String EXPIRY_DATE = "2025-03-01";

// Change execution limit
private static final int MAX_EXECUTIONS = 20;

// Change contact email
"Please contact your-email@example.com for a full license."
```

## Enhanced Protection with ProGuard

To make reverse engineering much harder, add ProGuard obfuscation:

### 1. Add ProGuard to pom.xml

Add this plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>com.github.wvengen</groupId>
    <artifactId>proguard-maven-plugin</artifactId>
    <version>2.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>proguard</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <proguardVersion>7.3.2</proguardVersion>
        <injar>${project.build.finalName}-jar-with-raks.jar</injar>
        <outjar>${project.build.finalName}-obfuscated.jar</outjar>
        <libs>
            <lib>${java.home}/jmods/java.base.jmod</lib>
        </libs>
        <options>
            <option>-dontshrink</option>
            <option>-dontoptimize</option>
            <option>-keepattributes *Annotation*</option>
            <option>-keep public class com.raks.muleguard.MuleGuardMain {
                public static void main(java.lang.String[]);
            }</option>
            <option>-keep class com.raks.muleguard.** { *; }</option>
        </options>
    </configuration>
</plugin>
```

### 2. Build with obfuscation

```bash
mvn clean package
```

This will create `muleguard-1.0.0-obfuscated.jar` which is much harder to reverse engineer.

## Distribution Checklist

Before distributing a trial version:

- [ ] Set appropriate `EXPIRY_DATE` in `TrialLicenseManager.java`
- [ ] Set appropriate `MAX_EXECUTIONS` in `TrialLicenseManager.java`
- [ ] Update contact email in error messages
- [ ] Build the JAR: `mvn clean package`
- [ ] Test the trial version yourself
- [ ] (Optional) Apply ProGuard obfuscation
- [ ] Rename JAR to indicate it's a trial: `muleguard-trial-1.0.0.jar`
- [ ] Create a README for users explaining trial limitations

## Trial User Experience

When users run the trial version, they will see:

```
╔════════════════════════════════════════════════════════════╗
║           MuleGuard - TRIAL VERSION                        ║
╠════════════════════════════════════════════════════════════╣
║  Trial expires: 2025-02-01 (45 days remaining)             ║
║  Runs remaining: 9 / 10                                    ║
║  Contact: raksjnu@gmail.com for full license               ║
╚════════════════════════════════════════════════════════════╝
```

When trial expires or limit is reached:

```
╔════════════════════════════════════════════════════════════╗
║                  LICENSE ERROR                             ║
╠════════════════════════════════════════════════════════════╣
║  Trial period has expired on 2025-02-01.                   ║
║  Please contact raksjnu@gmail.com for a full license.      ║
╚════════════════════════════════════════════════════════════╝
```

## Security Notes

1. **License File Location**: `~/.muleguard-trial.dat` in user's home directory
2. **Tampering Protection**: If users delete the file, it will be recreated but won't reset the counter (stored with first run date)
3. **Decompilation Risk**: Java bytecode can be decompiled. Use ProGuard for better protection.
4. **Not Foolproof**: Determined users can bypass these protections. This is meant to deter casual misuse.

## Support

For questions or to create custom licensing schemes, contact: raksjnu@gmail.com
