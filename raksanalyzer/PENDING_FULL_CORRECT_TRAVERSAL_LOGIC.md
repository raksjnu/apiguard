# PENDING: Full Correct Traversal Logic for TIBCO Diagrams

## Overview
This document outlines the complete requirements for implementing correct diagram generation logic for TIBCO processes, covering both **Service Integration Diagrams (Section 2)** and **Process Flow Diagrams (Section 3)**.

---

> [!IMPORTANT]
> **Current Status (2025-12-31): COMPLETED** 
> *   **Action**: Restored V2 logic for Integration Diagrams (Section 2) while retaining V3 logic for Flow Diagrams (Section 3).
> *   **Result**: "Hybrid" implementation in `TibcoDiagramGenerator.java` correctly handles groups, subprocess recursion, and circular references.
> *   **Verification**: Verified via `ManualTibcoAnalysisTest` and successful compilation.
> *   **Next Step**: User to review generated diagrams for final acceptance.


## Requirements Summary

### 1. Service Integration Diagrams (Section 2)
**Purpose**: Show integration flow with all connectors across all subprocess levels

#### 1.1 Starter Type Requirements
- ✅ **MUST** have starter-type activities (HTTP Receiver, JMS Queue Receiver, File Poller, Timer, etc.)
- ✅ **Exception**: Service Agents and REST Adapters (non-starter but treated as services)
- ✅ Show starter activity as the entry point

#### 1.2 Activity Display Rules
- ✅ **Show**: Only connector activities (REST, SOAP, JMS, File, Database, etc.)
- ✅ **Hide**: Non-connector activities (Assign, Log, Null, etc.) - unless in traversal path
- ✅ **Config**: `tibco.integration.show.all.activities=false`

#### 1.3 Subprocess Traversal
- ✅ **Recursive**: Traverse into ALL subprocess calls (CallProcessActivity)
- ✅ **Unlimited Depth**: No depth restrictions (safety limit: 50)
- ✅ **Show Nested Connectors**: Display connectors from all subprocess levels
- ✅ **Config**: `tibco.integration.traverse.subprocesses=true, maxDepth=50`

#### 1.4 Group Traversal
- ✅ **Groups within Groups**: Traverse nested groups (Loop, CriticalSection, etc.)
- ✅ **Show Group Boundaries**: Use PlantUML partitions for visual grouping
- ✅ **Loop Groups**: Detect and handle while/until loops

#### 1.5 Special Cases
- ✅ **Spawn Process**: Detect SpawnActivity, show with ⚡ symbol in yellow/orange partition
- ✅ **Dynamic Override**: Extract process name from `<pd:processPath>` XPath, show with 📞 symbol in blue partition
- ✅ **Circular References**: Detect A→B→A cycles, render "🔄 Recursive call to [ProcessName]" and exit
- ✅ **Loop Groups with Recursion**: Special handling for while/until loops that call back to parent

---

### 2. Process Flow Diagrams (Section 3)
**Purpose**: Show complete flow of a single process with all activities

#### 2.1 Starter Type Requirements
- ✅ **ANY Activity**: Can start with starter OR non-starter activity
- ✅ **Flexibility**: Process can be any .process file, not just services

#### 2.2 Activity Display Rules
- ✅ **Show**: ALL activities (Assign, Log, Null, Connectors, etc.)
- ✅ **Complete Flow**: Every activity in the process is rendered
- ✅ **Config**: `tibco.integration.show.all.activities=true`

#### 2.3 Subprocess Traversal
- ✅ **Single Level Only**: Do NOT traverse into subprocess calls
- ✅ **Show Call**: Render CallProcessActivity as a node, but don't expand it
- ✅ **Config**: `tibco.integration.traverse.subprocesses=false, maxDepth=0`

#### 2.4 Group Traversal
- ✅ **Groups within Groups**: Traverse nested groups (Loop, CriticalSection, etc.)
- ✅ **Show Group Boundaries**: Use PlantUML partitions for visual grouping
- ✅ **Complete Group Content**: Show all activities inside groups

#### 2.5 Special Cases
- ✅ **Spawn Process**: Show SpawnActivity node, don't traverse into spawned process
- ✅ **Dynamic Override**: Show CallProcessActivity with override, don't traverse
- ✅ **No Circular Detection Needed**: Single-level traversal prevents cycles

---

## Section 2 vs Section 3 Consistency Checklist

> [!CAUTION]
> **CRITICAL**: When modifying group rendering logic, ALWAYS ensure Section 2 and Section 3 remain consistent!

### ✅ Verified Consistency Rules (2026-01-01)

1. **Group Rendering - ALWAYS Use Partition**
   - ✅ Section 2 (`traverseProcess` line 392): `sb.append("partition \"").append(partitionLabel).append("\" {\n");`
   - ✅ Section 3 (`renderGroup` line 1167): `sb.append("partition \"").append(partitionLabel).append("\" {\n");`
   - ❌ **NEVER** add conditional logic like `if (!insideFork)` for partition rendering
   - **Reason**: PlantUML DOES support partitions inside fork branches - Section 2 proves this!

2. **No Special Handling for Groups Inside Forks**
   - ✅ Section 2: No `insideFork` parameter or conditional logic for group wrappers
   - ✅ Section 3: Removed `insideFork` conditional - always use partition
   - **Lesson Learned**: The "PlantUML limitation" was a misconception - partitions work fine inside forks

3. **Internal Fork Rendering**
   - ✅ Both sections: Groups with multiple start targets render internal `fork`/`end fork`
   - ✅ Both sections: Fork logic is based on transition count, not `insideFork` flag
   - **Key**: Internal parallel paths are determined by the group's structure, not external context

4. **Partition Closing**
   - ✅ Section 2: Always closes partition with `}`
   - ✅ Section 3: Always closes partition with `}`
   - ❌ **NEVER** make closing conditional on `insideFork` or any other flag

### 🔍 Debugging Checklist

When encountering "Cannot find group" or similar PlantUML errors:

1. **Compare Generated PUML**:
   - Check Section 2 Integration diagram for the same process
   - Check Section 3 Flow diagram for the same process
   - Look for syntax differences in group rendering

2. **Verify Partition Usage**:
   - Ensure both sections use `partition "Label" {` syntax
   - Confirm no conditional logic prevents partition rendering
   - Check that closing `}` is always present

3. **Check Fork Nesting**:
   - Verify internal forks are properly opened and closed
   - Ensure `fork`/`fork again`/`end fork` balance is correct
   - Confirm partitions don't interfere with fork structure

4. **Review Section 2 Code**:
   - Section 2 (Integration) is the "source of truth" for group rendering
   - If Section 2 works and Section 3 doesn't, Section 3 needs to match Section 2
   - Look for any logic in Section 3 that doesn't exist in Section 2

### 📝 Implementation Notes

**Section 2 Method**: `traverseProcess` (lines 336-650)
- Handles groups as part of main traversal
- Uses partition wrapper unconditionally
- Traverses group contents with standard logic

**Section 3 Method**: `renderGroup` (lines 1065-1270)
- Dedicated method for group rendering
- Must match Section 2's partition usage
- Should not add extra conditional logic

**Common Pitfall**: Adding "smart" conditional logic to Section 3 that doesn't exist in Section 2, causing divergence and errors.

**Solution**: Keep it simple - if Section 2 works, copy its approach exactly.

### 🔬 Debug Analysis - CONCLUSIVE FINDINGS (2026-01-01)

**Test Results:**
- Section 2: Renders 26+ groups with partitions - ZERO use of `insideFork` parameter
- Section 3: Skips 10+ groups with "Skipping partition for group 'Group' (insideFork=true)"

**Root Cause Identified:**
Section 2 does NOT have an `insideFork` parameter at all! It treats groups as regular traversal nodes:
```java
// Section 2 (Line 392) - ALWAYS renders partition
sb.append("partition \"").append(partitionLabel).append("\" {\n");
```

Section 3 incorrectly adds `insideFork` logic that doesn't exist in Section 2:
```java
// Section 3 (Line 1160) - WRONG APPROACH
if (!insideFork) {
    sb.append("partition \"").append(partitionLabel).append("\" {\n");
} else {
    // Skip partition - THIS IS THE BUG!
}
```

**The Fix:**
Remove ALL `insideFork` conditional logic from Section 3's `renderGroup` method. Groups should ALWAYS use partition wrappers, exactly like Section 2. The `insideFork` parameter itself is the problem - Section 2 proves it's unnecessary.

---

## Regression Checklist

### Section 2 (Integration Diagrams) - Must Pass
- [ ] `connector.process` - Shows all connectors across subprocess levels
- [ ] `orderServiceJMS.process` - Correct fork/merge logic, nested groups
- [ ] Circular reference detection works (A→B→A)
- [ ] Spawn process detection with ⚡ symbol
- [ ] Dynamic override detection with 📞 symbol
- [ ] Groups render with partition boundaries
- [ ] No "Cannot find fork" or "Cannot find group" errors

### Section 3 (Flow Diagrams) - Must Pass  
- [ ] `connector.process` - All activities shown, correct parallel paths
- [ ] `orderServiceJMS.process` - Complete flow with all activities
- [ ] Groups render with partition boundaries (matching Section 2)
- [ ] Internal forks within groups display correctly
- [ ] No subprocess recursion (single level only)
- [ ] No "Cannot find fork" or "Cannot find group" errors

### Code Quality
- [ ] No duplicate code between Section 2 and Section 3
- [ ] Consistent use of `LinkedHashMap`/`LinkedHashSet` for determinism
- [ ] Debug logging present for troubleshooting
- [ ] Comments explain any non-obvious logic


---

## Section 2 vs Section 3: Labeled Transition Syntax Analysis (2026-01-01)

### Root Cause Discovered

**Why Section 2 works with labeled transitions to groups:**

Section 2 uses a DIFFERENT PlantUML syntax for labeled transitions:
```
-> "testcondition";
partition "Group" {
```

Section 3 uses colored/bracketed syntax:
```
-[#483D8B]->[testcondition]
partition "Group" {
```

**PlantUML Parser Behavior:**
- ✅ `-> "label";` WORKS before partition blocks
- ❌ `-[#483D8B]->[label]` FAILS before partition blocks

The bracketed syntax `-[color]->[label]` confuses PlantUML's parser when followed immediately by a partition block inside a fork branch.

### Two Issues to Fix

#### Issue 1: Activities Showing as Yellow Labels
**Problem:** After labeled transitions in Section 2, activities appear as yellow text labels instead of proper activity shapes.

**Example from orderServiceJMS:**
```
-[#483D8B]->[REST API Call]
Log-1-1\nlog    ← Shows as yellow label, not activity shape
```

**Root Cause:** The activity is being rendered as part of the transition label text instead of as a separate activity node.

**Solution:** Need to ensure activity rendering happens AFTER the transition arrow, not as part of the label.

#### Issue 2: Section 3 Labeled Transitions to Groups
**Problem:** Section 3's colored/bracketed syntax `-[#483D8B]->[label]` causes "Cannot find group" errors before partition blocks.

**Solution Implemented:** Suppress transition labels when target is a group:
```java
boolean successorIsGroup = groupMap.containsKey(successor);
if (!successorIsGroup && transitionLabels != null ...) {
    // Add label
} else {
    sb.append("->\n");  // Plain arrow for groups
}
```

### Recommendations

1. **Keep Section 3 fix** - Suppressing labels for group targets is correct
2. **Fix activity rendering** - Investigate why activities after labeled transitions show as labels
3. **Consider syntax alignment** - Optionally change Section 3 to use Section 2's `-> "label";` syntax for consistency

### Code Locations

- **Section 2 transition labels**: Line 648 - `sb.append("-> \"").append(label).append("\";\n");`
- **Section 3 transition labels**: Line 941 - `sb.append("-[#483D8B]->[").append(label).append("]\\n");`
- **Section 3 group check**: Line 938 - `boolean successorIsGroup = groupMap.containsKey(successor);`
