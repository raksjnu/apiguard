const AEGIS_RULES_DATA = {
  "config" : {
    "environments" : [ "SBX", "ATDV", "TDV1", "TDV", "IBF", "MNE", "MNE1", "MNE2", "ITE", "PITE", "PREP", "DRL", "TRN", "PROD", "DR" ],
    "folderPattern" : null,
    "projectIdentification" : {
      "configFolder" : {
        "namePattern" : ".*_config.*"
      },
      "targetProject" : {
        "matchMode" : "ALL",
        "markerFiles" : [ "pom.xml", "mule-artifact.json" ]
      },
      "ignoredFolders" : {
        "exactNames" : [ "target", "bin", "build", ".git", ".idea", ".vscode", "node_modules", "__MACOSX" ],
        "prefixes" : [ "." ]
      }
    }
  },
  "rules" : [ {
    "id" : "RULE-000",
    "name" : "Generic Code Check - Forbidden Tokens",
    "description" : "Scans configured files for forbidden tokens. Fails if tokens are found.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "GENERIC_TOKEN_SEARCH_FORBIDDEN",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml", "src/main/resources/*.dwl" ],
        "tokens" : [ "SAMPLE_DONT_USE_TOKEN" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true
      }
    } ]
  }, {
    "id" : "RULE-009",
    "name" : "Remove unsupported error handling expressions",
    "description" : "Scans for legacy error expressions like error.errorType, error.muleMessage, etc., which are not supported in modern Mule 4 error handling.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "GENERIC_TOKEN_SEARCH_FORBIDDEN",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml", "src/main/resources/*.dwl" ],
        "tokens" : [ "error.errorType", "error.muleMessage", "error.exception", "error.errors" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true
      }
    } ]
  }, {
    "id" : "RULE-011",
    "name" : "Check for DLP references",
    "description" : "Scans for specific DLP-related property references in configuration and property files.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "GENERIC_TOKEN_SEARCH_FORBIDDEN",
      "params" : {
        "filePatterns" : [ "mule/http-proxy.xml", "resources/*.properties" ],
        "tokens" : [ "north.bound.dlp.flag", "south.bound.dlp.flag", "wmq.dlp.requestqueue" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true
      }
    } ]
  }, {
    "id" : "RULE-017",
    "name" : "[TEMPLATE] Required Token Search",
    "description" : "Template for ensuring required tokens exist in files. Enable and configure as needed.",
    "enabled" : false,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "GENERIC_TOKEN_SEARCH_REQUIRED",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "tokens" : [ "REQUIRED_TOKEN_EXAMPLE" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true
      }
    } ]
  }, {
    "id" : "RULE-001",
    "name" : "Update pom parent: MuleParentPom version:LATEST",
    "description" : "Parent pom must be com.raks.eapi:MuleParentPom:LATEST",
    "enabled" : true,
    "severity" : "LOW",
    "checks" : [ {
      "type" : "POM_VALIDATION_REQUIRED",
      "params" : {
        "validationType" : "PARENT",
        "parent" : {
          "groupId" : "com.raks.eapi",
          "artifactId" : "MuleParentPom",
          "version" : "LATEST"
        }
      }
    } ]
  }, {
    "id" : "RULE-002",
    "name" : "Update pom property: mule.maven.plugin.version to 4.9.0",
    "enabled" : true,
    "severity" : "LOW",
    "checks" : [ {
      "type" : "POM_VALIDATION_REQUIRED",
      "params" : {
        "validationType" : "PROPERTIES",
        "properties" : [ {
          "name" : "mule.maven.plugin.version",
          "expectedValue" : "4.5.0"
        }, {
          "name" : "app.runtime",
          "expectedValue" : "4.9.0"
        } ]
      }
    } ]
  }, {
    "id" : "RULE-006",
    "name" : "Add pom dependency: apimuleutilities for JCE Encrypt/Decrypt",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "POM_VALIDATION_REQUIRED",
      "params" : {
        "validationType" : "DEPENDENCIES",
        "dependencies" : [ {
          "groupId" : "com.raks.eapi",
          "artifactId" : "apimuleutilities"
        } ]
      }
    } ]
  }, {
    "id" : "RULE-003",
    "name" : "Remove pom plugin: maven-clean-plugin",
    "enabled" : true,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "POM_VALIDATION_FORBIDDEN",
      "params" : {
        "validationType" : "PLUGINS",
        "forbiddenPlugins" : [ {
          "groupId" : "org.apache.maven.plugins",
          "artifactId" : "maven-clean-plugin"
        }, {
          "groupId" : "org.apache.maven.plugins",
          "artifactId" : "maven-surefire-plugin"
        } ]
      }
    } ]
  }, {
    "id" : "RULE-004",
    "name" : "Remove pom dependency: spring-security-ldap, spring-security-web, mule-module-spring-config, and db2jcc_license_cu",
    "enabled" : true,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "POM_VALIDATION_FORBIDDEN",
      "params" : {
        "validationType" : "DEPENDENCIES",
        "forbiddenDependencies" : [ {
          "groupId" : "org.springframework.security",
          "artifactId" : "spring-security-ldap"
        }, {
          "groupId" : "org.springframework.security",
          "artifactId" : "spring-security-web"
        }, {
          "groupId" : "org.mule.modules",
          "artifactId" : "mule-module-spring-config"
        }, {
          "groupId" : "com.ibm.db2",
          "artifactId" : "db2jcc_license_cu"
        }, {
          "groupId" : "com.mulesoft.mule.core",
          "artifactId" : "mule-core-ee"
        }, {
          "groupId" : "com.mulesoft.mule.runtime.modules",
          "artifactId" : "mule-module-spring-config-ee"
        } ]
      }
    } ]
  }, {
    "id" : "RULE-005",
    "name" : "Validate IBM MQ Connector Cipher Suite",
    "description" : "The IBM MQ connector configuration must use the approved cipher suite.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "XML_ATTRIBUTE_EXISTS",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "propertyResolution" : true,
        "caseSensitive" : true,
        "elementAttributeSets" : [ {
          "element" : "ibm:connection",
          "attributes" : {
            "cipherSuite" : "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-013",
    "name" : "Check for 'type' attribute in crypto:jce-config",
    "description" : "Ensures that any crypto:jce-config element has the 'type' attribute defined.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "XML_ATTRIBUTE_EXISTS",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "elements" : [ "crypto:jce-config" ],
        "attributes" : [ "type" ]
      }
    } ]
  }, {
    "id" : "RULE-015",
    "name" : "Validate HTTP Listener Path",
    "description" : "The HTTP listener configuration must use the approved path.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "XML_ATTRIBUTE_EXISTS",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "propertyResolution" : true,
        "caseSensitive" : true,
        "elementAttributeSets" : [ {
          "element" : "http:listener",
          "attributes" : {
            "path" : "/test"
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-010",
    "name" : "Remove unsupported from/to ApplicationCode attributes",
    "description" : "Scans for fromApplicationCode or toApplicationCode attributes in logging connector elements.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "XML_ATTRIBUTE_NOT_EXISTS",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "elements" : [ "request_in", "request_out", "response_in", "response_out" ],
        "forbiddenAttributes" : [ "fromApplicationCode", "toApplicationCode" ]
      }
    } ]
  }, {
    "id" : "RULE-008",
    "name" : "Check for api-gateway:autodiscovery",
    "description" : "Verifies that the api-gateway:autodiscovery element is present in a Mule configuration file.",
    "enabled" : true,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "XML_XPATH_EXISTS",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "xpathExpressions" : [ {
          "xpath" : "//*[local-name()='autodiscovery']",
          "failureMessage" : "API Autodiscovery is not enabled. The <api-gateway:autodiscovery> element is missing."
        } ]
      }
    } ]
  }, {
    "id" : "RULE-018",
    "name" : "[TEMPLATE] Forbidden XPath Check",
    "description" : "Template for ensuring forbidden XPath expressions do NOT exist. Enable and configure as needed.",
    "enabled" : false,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "XML_XPATH_NOT_EXISTS",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "xpathExpressions" : [ {
          "xpath" : "//*[local-name()='forbidden-element']",
          "failureMessage" : "Forbidden element found in XML"
        } ]
      }
    } ]
  }, {
    "id" : "RULE-012",
    "name" : "Check for legacy JCE PBE encryption algorithm",
    "description" : "Scans for the usage of PBEWithHmacSHA256AndAES_256 in crypto:jce-encrypt-pbe elements.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "XML_ELEMENT_CONTENT_FORBIDDEN",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true,
        "elementTokenPairs" : [ {
          "element" : "crypto:jce-encrypt-pbe",
          "forbiddenTokens" : [ "PBEWithHmacSHA256AndAES_256" ]
        } ]
      }
    } ]
  }, {
    "id" : "RULE-014",
    "name" : "Check for toBase64 in set-variable",
    "description" : "Scans for the use of the 'toBase64' token within a 'set-variable' element.",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "XML_ELEMENT_CONTENT_FORBIDDEN",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true,
        "elementTokenPairs" : {
          "forbiddenTokens" : [ "toBase64" ]
        }
      }
    } ]
  }, {
    "id" : "RULE-019",
    "name" : "[TEMPLATE] Required Element Content",
    "description" : "Template for ensuring required content exists within XML elements. Enable and configure as needed.",
    "enabled" : false,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "XML_ELEMENT_CONTENT_REQUIRED",
      "params" : {
        "filePatterns" : [ "src/main/mule/*.xml" ],
        "matchMode" : "SUBSTRING",
        "caseSensitive" : true,
        "elementTokenPairs" : [ {
          "element" : "some:element",
          "requiredTokens" : [ "REQUIRED_CONTENT" ]
        } ]
      }
    } ]
  }, {
    "id" : "RULE-007",
    "name" : "Validate mule-artifact.json (Mule 4.9+, Java 17, secureProperties)",
    "description" : "Ensures mule-artifact.json meets minimum runtime and security requirements",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "JSON_VALIDATION_REQUIRED",
      "params" : {
        "filePattern" : "mule-artifact.json",
        "minVersions" : {
          "minMuleVersion" : "4.9.0"
        },
        "requiredFields" : {
          "javaSpecificationVersions" : "17"
        },
        "requiredElements" : [ "secureProperties" ]
      }
    } ]
  }, {
    "id" : "RULE-016",
    "name" : "[TEMPLATE] Forbidden JSON Elements",
    "description" : "Template for ensuring forbidden JSON elements do NOT exist. Enable and configure as needed.",
    "enabled" : false,
    "severity" : "MEDIUM",
    "checks" : [ {
      "type" : "JSON_VALIDATION_FORBIDDEN",
      "params" : {
        "filePattern" : "mule-artifact.json",
        "forbiddenElements" : [ "deprecatedField" ]
      }
    } ]
  }, {
    "id" : "RULE-100",
    "name" : "Mandatory substring check for .properties files",
    "description" : "Scans .properties files for required tokens (exact substring match)",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "MANDATORY_SUBSTRING_CHECK",
      "params" : {
        "fileExtensions" : [ ".properties" ],
        "caseSensitive" : true,
        "environments" : [ "ALL" ],
        "tokens" : [ "apiId" ]
      }
    } ]
  }, {
    "id" : "RULE-102",
    "name" : "Mandatory substring check for .policy files",
    "description" : "Scans .policy files for required tokens (exact substring match)",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "MANDATORY_SUBSTRING_CHECK",
      "params" : {
        "fileExtensions" : [ ".policy" ],
        "caseSensitive" : true,
        "environments" : [ "ALL" ],
        "tokens" : [ "http.protocols=HTTPS", "http.private.port=8081" ]
      }
    } ]
  }, {
    "id" : "RULE-107",
    "name" : "Forbidden substring check for .properties files",
    "description" : "Fails if forbidden tokens are found in .properties files",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "MANDATORY_SUBSTRING_CHECK",
      "params" : {
        "fileExtensions" : [ ".properties" ],
        "caseSensitive" : true,
        "searchMode" : "FORBIDDEN",
        "environments" : [ "ALL" ],
        "tokens" : [ "hardcoded.password", "TODO", "fixmelater" ]
      }
    } ]
  }, {
    "id" : "RULE-108",
    "name" : "Forbidden substring check for .policy files",
    "description" : "Fails if forbidden tokens are found in .policy files",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "MANDATORY_SUBSTRING_CHECK",
      "params" : {
        "fileExtensions" : [ ".policy" ],
        "caseSensitive" : true,
        "searchMode" : "FORBIDDEN",
        "environments" : [ "ALL" ],
        "tokens" : [ "deprecated.policy", "old.version" ]
      }
    } ]
  }, {
    "id" : "RULE-101",
    "name" : "Mandatory name-value check for .properties files",
    "description" : "Ensures required properties exist with correct values in .properties files",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "MANDATORY_PROPERTY_VALUE_CHECK",
      "params" : {
        "fileExtensions" : [ ".properties" ],
        "delimiter" : "=",
        "caseSensitiveNames" : true,
        "caseSensitiveValues" : true,
        "environments" : [ "ALL" ],
        "properties" : [ {
          "name" : "LogJsonFormat",
          "values" : [ "true", "false" ]
        }, {
          "name" : "anotherpropertycheck",
          "values" : [ "somevalue" ]
        } ]
      }
    } ]
  }, {
    "id" : "RULE-103",
    "name" : "Mandatory name-value check for .policy files",
    "description" : "Ensures required policy properties exist with correct values",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "MANDATORY_PROPERTY_VALUE_CHECK",
      "params" : {
        "fileExtensions" : [ ".policy" ],
        "delimiter" : "=",
        "caseSensitiveNames" : true,
        "caseSensitiveValues" : true,
        "environments" : [ "ALL" ],
        "properties" : [ {
          "name" : "headerinjection.policy.applied",
          "values" : [ "true" ]
        }, {
          "name" : "headerinjection.policy.version",
          "values" : [ "1.3.2", "1.3.3" ]
        }, {
          "name" : "ratelimit.policy.applied",
          "values" : [ "true" ]
        }, {
          "name" : "ratelimit.policy.version",
          "values" : [ "1.4.2" ]
        }, {
          "name" : "raks.compositeauthn.policy.applied",
          "values" : [ "true" ]
        }, {
          "name" : "raks.compositeauthn.policy.version",
          "values" : [ "1.4.2", "1.4.3" ]
        }, {
          "name" : "raks.authz.policy.applied",
          "values" : [ "true" ]
        }, {
          "name" : "raks.authz.policy.version",
          "values" : [ "1.2.2", "1.2.3" ]
        }, {
          "name" : "assetType",
          "values" : [ "rest", "soap", "batch" ]
        } ]
      }
    } ]
  }, {
    "id" : "RULE-105",
    "name" : "Optional name-value check for .properties files",
    "description" : "If property exists in .properties files, validates its value",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "OPTIONAL_PROPERTY_VALUE_CHECK",
      "params" : {
        "fileExtensions" : [ ".properties" ],
        "delimiter" : "=",
        "caseSensitiveNames" : true,
        "caseSensitiveValues" : false,
        "environments" : [ "ALL" ],
        "properties" : [ {
          "name" : "platform.config.analytics.enabled",
          "values" : [ "true", "false" ]
        }, {
          "name" : "requestPathEncryption",
          "values" : [ "Y", "N" ]
        } ]
      }
    } ]
  }, {
    "id" : "RULE-106",
    "name" : "Optional name-value check for .policy files",
    "description" : "If property exists in .policy files, validates its value",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "OPTIONAL_PROPERTY_VALUE_CHECK",
      "params" : {
        "fileExtensions" : [ ".policy" ],
        "delimiter" : "=",
        "caseSensitiveNames" : true,
        "caseSensitiveValues" : true,
        "environments" : [ "ALL" ],
        "properties" : [ {
          "name" : "mTLS-AuthZ.policy.version",
          "values" : [ "3.0.0", "3.0.1" ]
        }, {
          "name" : "wssencdec.policy.version",
          "values" : [ "3.0.0", "3.0.1" ]
        } ]
      }
    } ]
  }, {
    "id" : "RULE-109",
    "name" : "Optional regex pattern (ip addresses) check in .properties files",
    "description" : "Fails if forbidden regex patterns are found in .properties files",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "GENERIC_TOKEN_SEARCH",
      "params" : {
        "filePatterns" : [ "**/Properties/**/*.properties" ],
        "environments" : [ "ALL" ],
        "searchMode" : "FORBIDDEN",
        "matchMode" : "REGEX",
        "tokens" : [ "^(?![\\s]*[#!]).*\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b", "^(?![\\s]*[#!]).*\\blocalhost\\b", "^(?![\\s]*[#!]).*\\b127\\.0\\.0\\.1\\b" ]
      }
    } ]
  }, {
    "id" : "RULE-110",
    "name" : "Optional regex pattern (ip addresses) check in .policy files",
    "description" : "Fails if forbidden regex patterns are found in .policy files",
    "enabled" : true,
    "severity" : "HIGH",
    "checks" : [ {
      "type" : "GENERIC_TOKEN_SEARCH",
      "params" : {
        "filePatterns" : [ "**/Policies/*.policy", "**/Policies/**/*.policy" ],
        "environments" : [ "ALL" ],
        "searchMode" : "FORBIDDEN",
        "matchMode" : "REGEX",
        "tokens" : [ "^(?![\\s]*[#!]).*\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b", "^(?![\\s]*[#!]).*\\blocalhost\\b", "^(?![\\s]*[#!]).*\\b127\\.0\\.0\\.1\\b" ]
      }
    } ]

  }, {
    "id" : "RULE-B01",
    "name" : "Conditional: Batch API asset type",
    "description" : "If API name contains 'batch', the property asset.type must be 'batch'.",
    "enabled" : true,
    "severity" : "MEDIUM",
    "useCase" : "Ensures large volume APIs are tagged correctly for infrastructure scaling.",
    "rationale" : "Automated provisioning depends on correct asset type metadata.",
    "exampleGood" : "Project: user-batch-api, asset.type=batch",
    "exampleBad" : "Project: user-batch-api, asset.type=rest",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "PROJECT_CONTEXT",
          "params" : {
            "nameContains" : "batch",
            "ignoreCase" : true
          }
        } ],
        "onSuccess" : [ {
          "type" : "MANDATORY_PROPERTY_VALUE_CHECK",
          "params" : {
            "fileExtensions" : [ ".properties" ],
            "environments" : [ "ALL" ],
            "properties" : [ {
              "name" : "asset.type",
              "values" : [ "batch" ]
            } ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-C-101",
    "name" : "Conditional: MQ and DB Common Timeout",
    "description" : "Projects using both MQ and DB must define a 'commontimeout' property.",
    "enabled" : true,
    "severity" : "HIGH",
    "useCase" : "Cross-system timeout synchronization.",
    "rationale" : "Prevents partial transaction failures where one system times out much later than the other.",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "logic" : "AND",
        "preconditions" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='config' and contains(namespace-uri(), 'mq')]"
            } ]
          }
        }, {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='config' and contains(namespace-uri(), 'db')]"
            } ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "MANDATORY_PROPERTY_VALUE_CHECK",
          "params" : {
            "fileExtensions" : [ ".properties" ],
            "environments" : [ "ALL" ],
            "properties" : [ {
              "name" : "commontimeout",
              "values" : [ ".+" ]
            } ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-C-102",
    "name" : "Best Practice: HTTP Config Externalization",
    "description" : "Ensures HTTP Request configurations use externalized properties for timeouts.",
    "enabled" : true,
    "severity" : "HIGH",
    "useCase" : "Prevents environment-specific hardcoding.",
    "rationale" : "Hardcoded timeouts lead to different behaviors across Dev/UAT/Prod without proper control.",
    "exampleGood" : "<http:request-config responseTimeout='${http.timeout}' ...>",
    "exampleBad" : "<http:request-config responseTimeout='5000' ...>",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='request-config' and contains(namespace-uri(), 'http')]"
            } ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "XML_ATTRIBUTE_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "elementName" : "http:request-config",
            "attributeName" : "responseTimeout",
            "expectedValue" : "^\\$\\{.*\\}$",
            "matchMode" : "REGEX"
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-C-103",
    "name" : "Resilience: Salesforce Reconnection",
    "description" : "If Salesforce connector is used, a reconnection strategy must be defined.",
    "enabled" : true,
    "severity" : "MEDIUM",
    "useCase" : "Handling temporary SaaS connectivity issues.",
    "rationale" : "Standard reconnection prevents Mule from locking up if Salesforce is briefly unavailable.",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='sfdc-config']"
            } ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='sfdc-config']/*[local-name()='reconnection']",
              "failureMessage" : "Mandatory <reconnection> element missing for Salesforce config."
            } ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-C-104",
    "name" : "Arch: JMS Dependency Alignment",
    "description" : "If JMS connector is in POM, it must be used/configured in code.",
    "enabled" : true,
    "severity" : "LOW",
    "useCase" : "Resource optimization and library cleanliness.",
    "rationale" : "Unused dependencies bloat the JAR and increase security surface area.",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "POM_VALIDATION_REQUIRED",
          "params" : {
            "validationType" : "DEPENDENCIES",
            "dependencies" : [ {
              "artifactId" : "mule-jms-connector"
            } ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='config' and contains(namespace-uri(), 'jms')]"
            } ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-C-105",
    "name" : "Governance: Prod Secure Config",
    "description" : "If environment is PROD, the secure-prod file must exist.",
    "enabled" : true,
    "severity" : "CRITICAL",
    "useCase" : "Environment-specific security enforcement.",
    "rationale" : "Ensures Production credentials are never mixed with lower environments.",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "MANDATORY_PROPERTY_VALUE_CHECK",
          "params" : {
            "fileExtensions" : [ ".properties" ],
            "environments" : [ "ALL" ],
            "properties" : [ {
              "name" : "env",
              "values" : [ "PROD" ]
            } ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "FILE_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/resources/secure-prod.properties" ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-C-106",
    "name" : "Standard: APIKit Error Handling",
    "description" : "If APIKit is detected, a Global Error Handler must be present.",
    "enabled" : true,
    "severity" : "HIGH",
    "useCase" : "Consistent error responses for all experience APIs.",
    "rationale" : "Standardized error handling is critical for API consumer experience.",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='config' and contains(namespace-uri(), 'apikit')]"
            } ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='error-handler']",
              "failureMessage" : "APIKit detected but no <error-handler> found."
            } ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-EXP-01",
    "name" : "Conditional: Experience API Autodiscovery",
    "description" : "Experience APIs (-exp-) must have Autodiscovery configured.",
    "enabled" : true,
    "severity" : "HIGH",
    "useCase" : "Compliance with API Governance.",
    "rationale" : "Experience APIs are public-facing and MUST be managed by the gateway.",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "PROJECT_CONTEXT",
          "params" : {
            "nameContains" : "-exp-",
            "ignoreCase" : true
          }
        } ],
        "onSuccess" : [ {
          "type" : "XML_XPATH_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "xpathExpressions" : [ {
              "xpath" : "//*[local-name()='autodiscovery']",
              "failureMessage" : "Mandatory 'api-gateway:autodiscovery' element is missing"
            } ]
          }
        } ]
      }
    } ]
  }, {
    "id" : "RULE-SEC-01",
    "name" : "Conditional: Secure Property Usage",
    "description" : "If secure properties file exists, 'secure::' syntax must be used in Mule XML.",
    "enabled" : true,
    "severity" : "CRITICAL",
    "useCase" : "Encryption enforcement.",
    "rationale" : "Prevents plain-text property resolution when vault-encrypted files are present.",
    "exampleGood" : "value='${secure::db.password}'",
    "exampleBad" : "value='${db.password}'",
    "checks" : [ {
      "type" : "CONDITIONAL_CHECK",
      "params" : {
        "preconditions" : [ {
          "type" : "FILE_EXISTS",
          "params" : {
            "filePatterns" : [ "src/main/resources/secure-*.properties" ]
          }
        } ],
        "onSuccess" : [ {
          "type" : "GENERIC_TOKEN_SEARCH_REQUIRED",
          "params" : {
            "tokens" : [ "secure::" ],
            "filePatterns" : [ "src/main/mule/*.xml" ],
            "matchMode" : "SUBSTRING"
          }
        } ]
      }
    } ]
  } ]
};