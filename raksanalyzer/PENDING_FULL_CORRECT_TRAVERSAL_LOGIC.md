# PENDING: Full Correct Traversal Logic for TIBCO Diagrams

## Overview
This document outlines the complete requirements for implementing correct diagram generation logic for TIBCO processes, covering both **Service Integration Diagrams (Section 2)** and **Process Flow Diagrams (Section 3)**.

---

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
- ✅ **Dynamic Override**: Extract process name from `<pd:processPath>` XPath, show with 🔄 symbol in blue partition
- ✅ **Circular References**: Detect A→B→A cycles, render "⟲ Recursive call to [ProcessName]" and exit
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

## Unified Generator Configuration

### DiagramGenerationConfig Class
```java
public class DiagramGenerationConfig {
    // Activity Display
    boolean showAllActivities;          // true=all, false=connectors only
    
    // Subprocess Traversal
    boolean traverseSubprocesses;       // true=recursive, false=single level
    int maxDepth;                       // Max subprocess depth (50 for Section 2, 0 for Section 3)
    
    // Special Features
    boolean showSpawnOverride;          // Capture spawn/override details
    boolean detectCircularReferences;   // Enable cycle detection
    
    // Visual
    boolean usePartitions;              // Group subprocesses/groups in partitions
    int maxActivitiesPerPage;           // Page split threshold (50)
}
```

### Configuration Matrix

| Feature | Section 2 (Integration) | Section 3 (Flow) |
|---------|------------------------|------------------|
| `showAllActivities` | `false` (connectors only) | `true` (all activities) |
| `traverseSubprocesses` | `true` (recursive) | `false` (single level) |
| `maxDepth` | `50` | `0` |
| `showSpawnOverride` | `true` | `true` |
| `detectCircularReferences` | `true` | `false` |
| `usePartitions` | `true` | `true` |
| `maxActivitiesPerPage` | `50` | `50` |

---

## Detailed Feature Requirements

### Feature 1: Connector-Only Display (Section 2)
**Requirement**: Show only connector activities, hide non-connectors
- ✅ Connector patterns: REST, SOAP, JMS, File, Database, FTP, JDBC, etc.
- ✅ Non-connectors: Assign, Log, Null, Mapper, etc.
- ✅ Exception: Show non-connectors if they're in the path between connectors
- ✅ Implementation: Filter by activity type using connector patterns

### Feature 2: All Activities Display (Section 3)
**Requirement**: Show every activity in the process
- ✅ No filtering by type
- ✅ Render all: Assign, Log, Null, Connectors, etc.
- ✅ Implementation: No filtering, render all activities

### Feature 3: Recursive Subprocess Traversal (Section 2)
**Requirement**: Traverse into all subprocess calls, unlimited depth
- ✅ Detect `CallProcessActivity`
- ✅ Extract subprocess path from configuration
- ✅ Load subprocess .process file
- ✅ Parse and traverse subprocess activities
- ✅ Render in partition with subprocess name
- ✅ Continue recursively for nested subprocesses
- ✅ Track call chain for cycle detection

### Feature 4: Single-Level Traversal (Section 3)
**Requirement**: Show subprocess calls as nodes, don't expand
- ✅ Detect `CallProcessActivity`
- ✅ Render as activity node with subprocess name
- ✅ Do NOT load or traverse subprocess file
- ✅ Implementation: Skip recursion when `traverseSubprocesses=false`

### Feature 5: Nested Group Traversal (Both Sections)
**Requirement**: Traverse groups within groups
- ✅ Detect group types: Loop, CriticalSection, Pick, Scope, etc.
- ✅ Render outer group partition
- ✅ Traverse activities inside group
- ✅ If inner group found, render nested partition
- ✅ Continue recursively for any depth of group nesting
- ✅ Implementation: Recursive group rendering

### Feature 6: Circular Reference Detection (Section 2)
**Requirement**: Detect and handle A→B→A cycles
- ✅ Maintain call chain: `[ProcessA, ProcessB, ProcessC]`
- ✅ Before traversing subprocess, check if it's in call chain
- ✅ If found: Render `:⟲ Recursive call to [ProcessName];` and exit
- ✅ Special case: Loop groups (while/until) that call back to parent
- ✅ Implementation: CallChain class with contains() check

### Feature 7: Spawn Process Detection (Both Sections)
**Requirement**: Show spawned processes with special notation
- ✅ Detect activity type: `SpawnActivity`
- ✅ Extract spawned process name from configuration
- ✅ Section 2: Traverse into spawned process (if `traverseSubprocesses=true`)
- ✅ Section 3: Show as node only (don't traverse)
- ✅ Visual: Yellow/orange partition with ⚡ symbol
- ✅ Implementation: Check activity type, render with special partition

### Feature 8: Dynamic Override Detection (Both Sections)
**Requirement**: Extract and show dynamic subprocess calls
- ✅ Detect `CallProcessActivity` with `<pd:processPath>` element
- ✅ Parse XPath expression: `//pd:processDefinition[@name='ProcessName']`
- ✅ Use regex to extract process name from XPath
- ✅ Section 2: Traverse into override process (if `traverseSubprocesses=true`)
- ✅ Section 3: Show as node only (don't traverse)
- ✅ Visual: Blue partition with 🔄 symbol
- ✅ Implementation: XPath parsing with regex fallback

### Feature 9: Multi-Page Diagram Support (Both Sections)
**Requirement**: Split large diagrams across multiple pages
- ✅ Track activity count during traversal
- ✅ When count reaches threshold (50), split diagram
- ✅ Add continuation symbol: "→ Continued on next diagram"
- ✅ Generate multiple PlantUML strings
- ✅ Render multiple PNG files: `process_integration_1.png`, `_2.png`, etc.
- ✅ Update PDF/Word generators to embed all pages
- ✅ Implementation: Activity counter with threshold check

### Feature 10: Visual Partitions (Both Sections)
**Requirement**: Use PlantUML partitions for grouping
- ✅ Subprocess: `partition "Subprocess: [Name]" { ... }`
- ✅ Group: `partition "Group: [Type] - [Name]" { ... }`
- ✅ Spawn: `partition "⚡ Spawned: [Name]" #FFEBCD { ... }`
- ✅ Override: `partition "🔄 Override: [Name]" #E3F2FD { ... }`
- ✅ Cycle: `:⟲ Recursive call to [Name];`
- ✅ Implementation: PlantUML partition syntax

---

## Test Cases

### Test Case 1: Simple Subprocess (1 Level)
**Process**: A calls B
- ✅ Section 2: Show A's connectors, traverse to B, show B's connectors
- ✅ Section 3: Show all A's activities, show CallProcessActivity to B (don't traverse)

### Test Case 2: Nested Subprocess (3 Levels)
**Process**: A calls B, B calls C, C calls D
- ✅ Section 2: Show connectors from A, B, C, D in nested partitions
- ✅ Section 3: Show all A's activities, show CallProcessActivity to B (don't traverse)

### Test Case 3: Circular Reference
**Process**: A calls B, B calls A
- ✅ Section 2: Show A's connectors, traverse to B, detect cycle, render "⟲ Recursive call to A"
- ✅ Section 3: Show all A's activities, show CallProcessActivity to B (don't traverse)

### Test Case 4: Loop Group with Recursion
**Process**: A has while loop that calls A
- ✅ Section 2: Show loop group, detect cycle, render "⟲ Recursive call to A"
- ✅ Section 3: Show loop group with CallProcessActivity to A (don't traverse)

### Test Case 5: Spawn Process
**Process**: A spawns B
- ✅ Section 2: Show A's connectors, traverse to B in ⚡ partition, show B's connectors
- ✅ Section 3: Show all A's activities including SpawnActivity (don't traverse to B)

### Test Case 6: Dynamic Override
**Process**: A calls B with `processPath` XPath
- ✅ Section 2: Extract B name from XPath, traverse to B in 🔄 partition, show B's connectors
- ✅ Section 3: Show CallProcessActivity with override (don't traverse to B)

### Test Case 7: Large Diagram (>50 Activities)
**Process**: A has 60 activities
- ✅ Section 2: Split at 50 activities, generate `_1.png` and `_2.png`
- ✅ Section 3: Split at 50 activities, generate `_1.png` and `_2.png`

### Test Case 8: Nested Groups
**Process**: A has Loop group containing CriticalSection group
- ✅ Section 2: Show outer Loop partition, inner CriticalSection partition, connectors inside
- ✅ Section 3: Show outer Loop partition, inner CriticalSection partition, all activities inside

### Test Case 9: Service Agent (Non-Starter Service)
**Process**: Service Agent with operations
- ✅ Section 2: Treat as service, show operations and connectors
- ✅ Section 3: N/A (Service Agents don't have flow diagrams)

### Test Case 10: REST Adapter (Non-Starter Service)
**Process**: REST Adapter service
- ✅ Section 2: Treat as service, show REST operations and connectors
- ✅ Section 3: N/A (REST Adapters don't have flow diagrams)

---

## Implementation Checklist

### Phase 1: Test Infrastructure ✅
- [ ] Create `src/test/java/DiagramGeneratorTest.java`
- [ ] Create `test-diagrams-only.properties`
- [ ] Create `test-diagrams.bat` script
- [ ] Setup test output directory

### Phase 2: DiagramConfig Class ✅
- [ ] Create `DiagramGenerationConfig` class
- [ ] Add all configuration fields
- [ ] Modify `generateIntegrationPuml` to accept config
- [ ] Modify `generateFlowPuml` to accept config

### Phase 3: Cycle Detection ✅
- [ ] Create `CallChain` class
- [ ] Modify `traverseProcess` to accept call chain
- [ ] Implement cycle detection logic
- [ ] Render cycle notation in PlantUML

### Phase 4: Spawn/Override ✅
- [ ] Detect `SpawnActivity`
- [ ] Extract process name from spawn config
- [ ] Detect dynamic override in `CallProcessActivity`
- [ ] Parse XPath from `processPath` element
- [ ] Render with special partitions and symbols

### Phase 5: Multi-Page Support ✅
- [ ] Add activity counter to traversal
- [ ] Implement page split logic
- [ ] Generate multiple PlantUML strings
- [ ] Render multiple PNG files
- [ ] Update PDF/Word generators to handle multiple diagrams

### Phase 6: Unify Sections ✅
- [ ] Update Section 2 to use config-based generator
- [ ] Update Section 3 to use config-based generator
- [ ] Remove duplicate diagram generation logic
- [ ] Test both sections with unified generator

### Phase 7: Testing ✅
- [ ] Run all 10 test cases
- [ ] Visual review of generated diagrams
- [ ] Verify all requirements are met
- [ ] Performance testing with deep nesting

---

## Success Criteria

✅ **All 10 test cases pass**
✅ **Section 2 shows only connectors across all subprocess levels**
✅ **Section 3 shows all activities in single process only**
✅ **Circular references detected and rendered correctly**
✅ **Spawn and override processes shown with special notation**
✅ **Large diagrams split across multiple pages**
✅ **Nested groups rendered with proper partitions**
✅ **Same generator code works for both Section 2 and Section 3**
✅ **No infinite loops or performance issues**
✅ **Visual clarity maintained even with deep nesting**

---

## Configuration Properties

```properties
# ===== Section 2: Integration Diagrams =====
tibco.integration.show.all.activities=false
tibco.integration.traverse.subprocesses=true
tibco.integration.max.depth=50
tibco.integration.max.activities.per.page=50
tibco.integration.use.partitions=true
tibco.integration.show.spawn.override=true
tibco.integration.detect.circular.references=true

# ===== Section 3: Flow Diagrams =====
tibco.flow.show.all.activities=true
tibco.flow.traverse.subprocesses=false
tibco.flow.max.depth=0
tibco.flow.max.activities.per.page=50
tibco.flow.use.partitions=true
tibco.flow.show.spawn.override=true
tibco.flow.detect.circular.references=false
```

---

## Notes

- This is a **PENDING** feature - not yet implemented
- Estimated effort: 2-3 days of development + testing
- High complexity due to recursive traversal and cycle detection
- Critical for accurate connector visibility in integration diagrams
- Will significantly improve documentation quality for complex TIBCO projects
