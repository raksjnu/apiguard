# JSON Canonicalization & Smart Comparison Research

## Summary

Yes, there are excellent libraries for JSON canonicalization and semantic comparison in Java, similar to what we have for XML. The best options depend on your use case:

## Recommended Libraries

### 1. **JSONassert** (Best for Semantic Comparison)
- **Maven**: `org.skyscreamer:jsonassert`
- **Use Case**: Comparing JSON structures while ignoring order
- **Key Features**:
  - `JSONCompareMode.LENIENT` - Ignores field order in objects and element order in arrays
  - Clear, human-readable error messages showing differences
  - Flexible comparison modes (STRICT, LENIENT, NON_EXTENSIBLE)
  - Most popular choice for JSON comparison in Java

**Example**:
```java
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

JSONAssert.assertEquals(expected, actual, JSONCompareMode.LENIENT);
```

### 2. **hamcrest-json** (Best for Expressive Assertions)
- **Maven**: `uk.co.datumedge:hamcrest-json`
- **Use Case**: Hamcrest-style matchers for JSON
- **Key Features**:
  - `sameJSONAs()` matcher
  - `allowingAnyArrayOrdering()` - Ignores array element order
  - `allowingExtraUnexpectedFields()` - Ignores extra fields
  - Integrates with JUnit/TestNG

**Example**:
```java
import static uk.co.datumedge.hamcrest.json.SameJSONAs.*;

assertThat(actual, sameJSONAs(expected)
    .allowingAnyArrayOrdering()
    .allowingExtraUnexpectedFields());
```

### 3. **Jackson** (Already in Your Project!)
- **Maven**: `com.fasterxml.jackson.databind`
- **Use Case**: Lightweight comparison using existing dependency
- **Key Features**:
  - `JsonNode.equals()` automatically ignores object key order
  - **Limitation**: Arrays are compared with strict order
  - Can convert to `Map<String, Object>` for custom comparison

**Example**:
```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();
JsonNode node1 = mapper.readTree(json1);
JsonNode node2 = mapper.readTree(json2);
boolean equal = node1.equals(node2); // Ignores object key order
```

### 4. **RFC 8785 Canonicalization** (For Hashing/Signing)
- **Maven**: `io.github.erdtman:java-json-canonicalization`
- **Use Case**: Deterministic JSON representation for cryptographic purposes
- **Key Features**:
  - Follows RFC 8785 standard
  - Strips whitespace
  - Sorts object keys lexicographically
  - **Does NOT change array order** (per RFC spec)

**Example**:
```java
import io.github.erdtman.jcs.JsonCanonicalizer;

String canonical = new JsonCanonicalizer(jsonString).getEncodedString();
```

## Comparison Matrix

| Library | Ignores Object Key Order | Ignores Array Order | Error Messages | Canonicalization |
|---------|-------------------------|---------------------|----------------|------------------|
| **JSONassert** | ✅ Yes | ✅ Yes (LENIENT mode) | ✅ Excellent | ❌ No |
| **hamcrest-json** | ✅ Yes | ✅ Yes (with flag) | ✅ Good | ❌ No |
| **Jackson** | ✅ Yes | ❌ No | ⚠️ Basic | ❌ No |
| **RFC 8785** | ✅ Yes (sorts keys) | ❌ No | N/A | ✅ Yes |

## Recommendation for GitAnalyzer

For your use case (comparing JSON API responses), I recommend:

### **Primary Choice: JSONassert**
```xml
<dependency>
    <groupId>org.skyscreamer</groupId>
    <artifactId>jsonassert</artifactId>
    <version>1.5.1</version>
</dependency>
```

**Why?**
- Specifically designed for semantic JSON comparison
- Handles both object key order and array element order
- Provides detailed diff output showing exactly what changed
- Widely used and well-maintained
- Minimal learning curve

### Implementation Approach

Similar to your XML smart comparison, you could:

1. **Add JSONassert dependency** to `pom.xml`
2. **Create `JsonComparator` class** in `com.raks.gitanalyzer.comparison`
3. **Implement semantic comparison**:
   ```java
   public class JsonComparator {
       public static ComparisonResult compareJson(String json1, String json2) {
           try {
               JSONAssert.assertEquals(json1, json2, JSONCompareMode.LENIENT);
               return new ComparisonResult(true, "JSON documents are semantically equivalent");
           } catch (JSONCompareException e) {
               return new ComparisonResult(false, e.getMessage());
           }
       }
   }
   ```

4. **Integrate with `AnalyzerService`**:
   - Detect JSON files by extension (`.json`)
   - Use `JsonComparator` instead of text diff
   - Display semantic differences in the UI

## Alternative: Use Jackson (Already Available)

If you want to avoid adding a new dependency, you can use Jackson with custom logic:

```java
public static boolean compareJsonSemantically(String json1, String json2) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node1 = mapper.readTree(json1);
    JsonNode node2 = mapper.readTree(json2);
    
    // For objects: equals() ignores key order
    // For arrays: you'd need custom logic to sort/compare
    return node1.equals(node2);
}
```

**Limitation**: This won't handle array order differences, so JSONassert is still recommended.

## Next Steps

Would you like me to:
1. Implement JSON smart comparison using JSONassert?
2. Add it to the existing `AnalyzerService` alongside XML comparison?
3. Update the UI to show "Smart JSON Comparison" similar to XML?
