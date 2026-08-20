Yes. For an open-source contributor, the README should be more than a project description. It should function as a **technical handoff document**: what ArchMorph is, why it exists, how it works internally, what has already been solved, what the current output proves, what is still wrong, and exactly where a contributor can work.

Below is the version I recommend using as the **main `README.md`**.

````markdown
# ArchMorph

<p align="center">

# ArchMorph

### AI-Assisted Java Architecture Analysis & Modular Monolith Transformation

**Understand → Analyze → Discover → Plan → Transform → Validate**

</p>

<p align="center">

An open-source Java/Spring platform that analyzes existing applications,
reconstructs their architectural structure, detects architectural problems,
discovers business modules, and aims to transform layered applications into
well-structured modular monoliths.

</p>

---

# 🚧 Project Status

> **Status: Active Development / Open Source / Experimental**

ArchMorph currently has a working end-to-end architecture-analysis pipeline.

The project can already:

- accept a Java project as a ZIP archive
- create an isolated workspace
- extract and validate the project
- detect Java source files
- parse Java source code
- classify application components
- construct a dependency graph
- detect architectural layer violations
- detect circular dependencies
- discover candidate modules
- generate transformation mappings
- plan target layouts
- generate PlantUML architecture diagrams
- generate a transformed project workspace

However, the project is **not yet a production-grade automatic refactoring engine**.

The most important remaining work is:

- improving semantic dependency extraction
- improving business-module discovery
- preventing incorrect module duplication
- improving transformation correctness
- rewriting packages/imports safely
- validating generated projects
- preserving application behavior
- improving automated tests
- supporting multiple architecture strategies

---

# 📊 Current Implementation Status

ArchMorph is approximately:

> **~70% implemented as a project architecture/pipeline**

but approximately:

> **~45–50% functionally reliable for arbitrary real-world projects**

This distinction is important.

The majority of the architectural pipeline exists, but some of the most difficult intelligence-heavy components are still experimental.

```text
Project Upload              █████████████████░░░ 85%
Workspace Management        ██████████████████░░ 90%
Java Source Scanning        ██████████████████░░ 90%
Java Parsing                ██████████████████░░ 90%
Component Classification    ███████████████░░░░░ 75%
Dependency Analysis         ███████████░░░░░░░░░ 55%
Architecture Analysis       ██████████████░░░░░░ 70%
Cycle Detection             █████████████░░░░░░░ 65%
Module Discovery            ██████░░░░░░░░░░░░░░ 30%
Transformation Planning    ████████░░░░░░░░░░░░ 40%
Transformation Execution    █████░░░░░░░░░░░░░░░ 25%
Validation                  ████░░░░░░░░░░░░░░░░ 20%
Testing                     ███████░░░░░░░░░░░░░ 35%
````

These percentages represent engineering maturity estimates, not formal benchmarks.

---

# 🧠 What Problem Does ArchMorph Solve?

Large Java applications frequently start with a conventional layered architecture:

```text
controller/
service/
repository/
entity/
dto/
config/
security/
exception/
```

Initially this is simple.

As the application grows, however, multiple business domains begin sharing the same layers.

For example:

```text
controller/
    UserController
    OrderController
    PaymentController
    ProductController

service/
    UserService
    OrderService
    PaymentService
    ProductService

repository/
    UserRepository
    OrderRepository
    PaymentRepository
    ProductRepository
```

The business boundaries become difficult to see.

ArchMorph attempts to recover those boundaries automatically.

---

# 🎯 Long-Term Objective

The long-term objective is to transform:

```text
Traditional Layered Application
```

into:

```text
Modular Monolith
```

For example:

```text
BEFORE

controller/
service/
repository/
entity/
dto/
security/
config/
```

into:

```text
AFTER

modules/
│
├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
│
├── order/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
│
├── payment/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
│
└── shared/
    ├── security/
    ├── configuration/
    ├── exception/
    └── infrastructure/
```

The goal is **not merely moving files**.

ArchMorph must understand the application before restructuring it.

---

# 🏗️ Core Architecture

The current ArchMorph pipeline is:

```text
                    Java Project
                         │
                         ▼
                  Upload Controller
                         │
                         ▼
                Project Upload Service
                         │
                         ▼
                Project Validation
                         │
                         ▼
                 ZIP Extraction
                         │
                         ▼
                  Workspace Manager
                         │
                         ▼
                   Source Scanner
                         │
                         ▼
                  JavaParser Service
                         │
                         ▼
                 Component Analyzer
                         │
                         ▼
              Project Class Registry
                         │
                         ▼
                Dependency Graph
                         │
             ┌───────────┼────────────┐
             │           │            │
             ▼           ▼            ▼
        Architecture    Cycle       Module
         Analysis     Detection    Discovery
             │           │            │
             └───────────┼────────────┘
                         ▼
                Transformation Planner
                         │
                         ▼
              Transformation Mapping
                         │
                         ▼
                 Layout Planner
                         │
                         ▼
               Transformation Engine
                         │
                         ▼
                Transformed Project
                         │
                         ▼
                    Validation
```

---

# 🧩 Major Modules of ArchMorph

The source code is currently organized approximately as follows:

```text
src/main/java/com/anvith/archmorph/

├── ArchMorphApplication.java
│
├── common/
│   ├── config/
│   │   └── WorkspaceProperties.java
│   │
│   ├── constants/
│   │   └── WorkspaceConstants.java
│   │
│   ├── exception/
│   │   ├── ArchiveStorageException.java
│   │   ├── GlobalExceptionHandler.java
│   │   ├── InvalidProjectStructureException.java
│   │   ├── InvalidZipException.java
│   │   ├── JavaParsingException.java
│   │   ├── ProjectExtractionException.java
│   │   ├── SourceCodeNotFoundException.java
│   │   └── WorkspaceCreationException.java
│   │
│   ├── response/
│   │   └── ApiResponse.java
│   │
│   └── util/
│       ├── FileUtil.java
│       ├── ProjectIdGenerator.java
│       └── ZipUtil.java
│
├── parser/
│   ├── AnnotationConstants.java
│   ├── ClassMetadata.java
│   ├── ComponentAnalyzer.java
│   ├── ComponentType.java
│   ├── JavaParserService.java
│   ├── ProjectStructureDetector.java
│   └── SourceScanner.java
│
├── graph/
│   ├── GraphTraversalEngine.java
│   └── DefaultGraphTraversalEngine.java
│
├── analysis/
│   │
│   ├── architecture/
│   │   ├── ArchitectureAnalyzer.java
│   │   ├── ArchitectureReport.java
│   │   ├── DefaultArchitectureAnalyzer.java
│   │   ├── LayerAnalyzer.java
│   │   ├── LayerViolationDetector.java
│   │   └── rules/
│   │       ├── LayerRule.java
│   │       ├── DefaultLayerRules.java
│   │       └── LayerRuleRegistry.java
│   │
│   ├── cycle/
│   │   ├── CircularDependencyDetector.java
│   │   ├── CycleReport.java
│   │   └── DefaultCircularDependencyDetector.java
│   │
│   ├── dependency/
│   │   ├── DependencyType.java
│   │   ├── DependencyNode.java
│   │   ├── DependencyEdge.java
│   │   ├── DependencyGraph.java
│   │   ├── DependencyNodeFactory.java
│   │   ├── DependencyGraphBuilder.java
│   │   │
│   │   ├── extractor/
│   │   │   ├── DependencyExtractor.java
│   │   │   ├── ConstructorDependencyExtractor.java
│   │   │   ├── FieldDependencyExtractor.java
│   │   │   └── MethodParameterDependencyExtractor.java
│   │   │
│   │   └── util/
│   │       └── DependencyUtils.java
│   │
│   ├── diagram/
│   │   ├── DiagramGenerator.java
│   │   ├── DiagramReport.java
│   │   └── PlantUmlDiagramGenerator.java
│   │
│   ├── module/
│   │   ├── ModuleClassifier.java
│   │   ├── ModuleDiscoveryEngine.java
│   │   ├── DefaultModuleDiscoveryEngine.java
│   │   ├── ModuleInfo.java
│   │   ├── ModuleDiscoveryReport.java
│   │   ├── NamingHeuristicClassifier.java
│   │   │
│   │   ├── extractor/
│   │   │   ├── BusinessModuleExtractor.java
│   │   │   └── DefaultBusinessModuleExtractor.java
│   │   │
│   │   ├── graph/
│   │   │   └── ConnectedComponentFinder.java
│   │   │
│   │   ├── naming/
│   │   │   └── ModuleNamingStrategy.java
│   │   │
│   │   └── optimizer/
│   │       ├── ModuleOptimizer.java
│   │       └── DefaultModuleOptimizer.java
│   │
│   ├── registry/
│   │   ├── ProjectClassRegistry.java
│   │   ├── ProjectClassInfo.java
│   │   ├── ProjectClassCollector.java
│   │   └── DefaultProjectClassRegistry.java
│   │
│   └── transformation/
│       ├── TransformationEngine.java
│       ├── DefaultTransformationEngine.java
│       ├── FolderClassifier.java
│       ├── DefaultFolderClassifier.java
│       ├── FolderType.java
│       ├── LayoutPlanner.java
│       ├── DefaultLayoutPlanner.java
│       ├── ModuleLayout.java
│       ├── ModuleLayoutReport.java
│       │
│       ├── mapping/
│       │   ├── TransformationMappingEngine.java
│       │   ├── TransformationMapping.java
│       │   ├── DefaultTransformationMappingEngine.java
│       │   └── TransformationMappingReport.java
│       │
│       ├── packaging/
│       │   ├── BasePackageResolver.java
│       │   ├── DefaultBasePackageResolver.java
│       │   ├── PackagePlanner.java
│       │   └── DefaultPackagePlanner.java
│       │
│       └── planner/
│           ├── TransformationPlanner.java
│           ├── TransformationPlan.java
│           ├── TransformationPlanEntry.java
│           └── DefaultTransformationPlanner.java
│
└── upload/
    ├── controller/
    │   └── UploadController.java
    │
    ├── dto/
    │   └── UploadResponse.java
    │
    ├── model/
    │   └── UploadedProject.java
    │
    └── service/
        ├── ArchiveStorageService.java
        ├── ArchiveStorageServiceImpl.java
        ├── ProjectUploadService.java
        ├── ProjectValidator.java
        ├── WorkspaceManager.java
        ├── ZipExtractionService.java
        ├── ZipExtractionServiceImpl.java
        └── impl/
            └── ProjectUploadServiceImpl.java
```

---

# 🛠️ Technologies Used

## Java 17

The primary implementation language.

Java is used because ArchMorph operates directly on Java source code and needs access to:

* AST structures
* classes
* interfaces
* annotations
* packages
* generics
* inheritance
* method signatures

---

# Spring Boot

Spring Boot provides the backend application framework.

Used for:

* REST APIs
* dependency injection
* configuration
* exception handling
* service architecture
* application lifecycle

---

# Maven

Maven manages:

* dependencies
* compilation
* tests
* Spring Boot execution
* packaging

The repository includes:

```text
mvnw
mvnw.cmd
```

so contributors can build the project without manually installing Maven.

---

# JavaParser

JavaParser is one of the most important technologies in ArchMorph.

It is used to parse Java source code into an AST.

Conceptually:

```text
Java Source
     │
     ▼
JavaParser
     │
     ▼
AST
     │
     ├── Class
     ├── Method
     ├── Field
     ├── Annotation
     ├── Parameter
     ├── Import
     └── Type
```

This AST becomes the foundation for architecture analysis.

---

# PlantUML

PlantUML is used to generate architecture diagrams.

The long-term goal is to visualize:

```text
Classes
Dependencies
Layers
Modules
Cycles
Architecture violations
```

---

# JUnit

JUnit is used for automated testing.

Future testing will heavily depend on project fixtures that represent different architecture patterns.

---

# 📚 Core Concepts Used

ArchMorph combines several software engineering concepts.

---

## 1. Abstract Syntax Trees

Java source code is parsed into an AST.

This allows ArchMorph to inspect source code semantically instead of relying only on text matching.

---

## 2. Static Analysis

ArchMorph analyzes code without executing the user's application.

Examples:

```text
Class relationships
Dependency relationships
Annotations
Packages
Architecture layers
Cycles
```

---

## 3. Dependency Graphs

The application is represented as a directed graph:

```text
A → B
```

meaning:

```text
A depends on B
```

Example:

```text
AuthController
       │
       ├── UserService
       │
       └── UserRepository
```

---

## 4. Graph Traversal

DFS/BFS-style traversal is used to explore dependencies.

This is useful for:

* dependency analysis
* connected components
* cycle detection
* module discovery

---

## 5. Circular Dependency Detection

Cycles can be represented as:

```text
A → B → C → A
```

These are architecture smells that should be detected before transformation.

---

## 6. Layered Architecture

ArchMorph understands concepts such as:

```text
Controller
Service
Repository
Entity
DTO
Configuration
```

and can enforce rules between them.

---

## 7. Modular Monolith

The target architecture is a modular monolith.

The application remains one deployable application but contains strong internal module boundaries.

---

## 8. Cohesion

Classes belonging to the same module should have strong internal relationships.

High cohesion is desirable.

---

## 9. Coupling

Modules should minimize unnecessary dependencies on one another.

Low coupling is desirable.

---

## 10. Domain-Oriented Design

ArchMorph attempts to identify business capabilities instead of blindly preserving technical package structures.

---

## 11. Architecture Rules

Rules define which layers/components can depend on which others.

Example:

```text
Controller
    ↓
Service
    ↓
Repository
```

but:

```text
Controller
    ✕
Repository
```

---

## 12. Program Transformation

The eventual transformation process involves:

```text
Source AST
   ↓
Transformation Plan
   ↓
Package Mapping
   ↓
File Mapping
   ↓
Source Rewriting
   ↓
Generated Project
```

---

# 🧪 Benchmark Project: Layered Architecture

A Spring Boot layered application was used as a primary test fixture.

The project contains approximately:

```text
24 production Java classes
4 test classes
Maven configuration
Spring configuration
application profiles
logging configuration
```

Its source structure contains:

```text
config/
controller/
dto/
entity/
exception/
repository/
security/
service/
util/
```

This project is intentionally useful because it contains:

* controllers
* services
* repositories
* entity
* DTOs
* JWT security
* configuration
* exception handling
* tests

It therefore provides a realistic test case for architecture reconstruction.

---

# 📊 Current Analysis Output

The current ArchMorph run produced:

```text
========================================
ARCHMORPH AI ANALYSIS
========================================

Project ID      : PROJECT-11A87610

Project Root    :
workspace\projects\PROJECT-11A87610\original\layered-architecture-main

Java Files      : 24
```

---

# 🔍 Component Analysis Result

Current output:

```text
========== COMPONENT ANALYSIS ==========

ApplicationConfig                   -> CONFIGURATION
SecurityConfig                      -> CONFIGURATION
ApiResponse                         -> DTO
AuthController                      -> CONTROLLER
UserController                      -> CONTROLLER
LoginRequest                        -> DTO
SignupRequest                       -> DTO
UserRequest                         -> DTO
JwtAuthResponse                     -> DTO
UserResponse                        -> DTO
UserDto                             -> DTO
User                                -> ENTITY
BadRequestException                 -> EXCEPTION
GlobalExceptionHandler              -> EXCEPTION_HANDLER
ResourceNotFoundException           -> EXCEPTION
UserRepository                      -> REPOSITORY
CustomUserDetailsService            -> SERVICE
JwtAuthenticationFilter             -> COMPONENT
JwtTokenProvider                    -> COMPONENT
UserPrincipal                       -> UNKNOWN
UserService                         -> SERVICE
UserServiceImpl                     -> SERVICE
TemplateApplication                 -> APPLICATION
AppConstants                        -> UNKNOWN
```

This proves that the source scanner and basic classifier are already functioning.

---

# 🕸️ Current Dependency Graph

Current result:

```text
===============================================
PROJECT DEPENDENCY GRAPH
===============================================

Nodes : 24
Edges : 20
```

Current dependencies detected include:

```text
SecurityConfig --(FIELD)--> JwtAuthenticationFilter

AuthController --(FIELD)--> UserRepository
AuthController --(FIELD)--> UserService
AuthController --(FIELD)--> JwtTokenProvider

AuthController --(METHOD_PARAMETER)--> LoginRequest
AuthController --(METHOD_PARAMETER)--> SignupRequest

UserController --(FIELD)--> UserService
UserController --(METHOD_PARAMETER)--> UserRequest
UserController --(METHOD_PARAMETER)--> UserDto

GlobalExceptionHandler
    --(METHOD_PARAMETER)--> ResourceNotFoundException

GlobalExceptionHandler
    --(METHOD_PARAMETER)--> BadRequestException

CustomUserDetailsService
    --(FIELD)--> UserRepository

JwtAuthenticationFilter
    --(FIELD)--> JwtTokenProvider

UserPrincipal
    --(METHOD_PARAMETER)--> User

UserService
    --(METHOD_PARAMETER)--> UserRequest

UserService
    --(METHOD_PARAMETER)--> UserDto

UserServiceImpl
    --(FIELD)--> UserRepository

UserServiceImpl
    --(METHOD_PARAMETER)--> UserRequest

UserServiceImpl
    --(METHOD_PARAMETER)--> UserDto

UserServiceImpl
    --(METHOD_PARAMETER)--> User
```

This proves that the graph-building pipeline is operational.

However, dependency extraction is still incomplete.

---

# 🏛️ Current Architecture Report

ArchMorph currently produces:

```text
===============================================
ARCHITECTURE REPORT
===============================================

Controllers      : 2
Services         : 3
Repositories     : 1
Entities         : 1
Components       : 2
Configurations   : 2
Dependencies     : 20
```

Current violation:

```text
CONTROLLER 'AuthController'
must not depend on REPOSITORY 'UserRepository'
```

This is an important success.

ArchMorph has correctly detected a genuine architectural problem in the test application.

---

# 🔄 Current Circular Dependency Result

Current result:

```text
===============================================
CIRCULAR DEPENDENCY REPORT
===============================================

No circular dependencies found.
```

---

# 🧩 Current Module Discovery Result

Current result:

```text
===============================================
DISCOVERED MODULES
===============================================

AuthController
   - AuthController
   - UserRepository
   - CustomUserDetailsService
   - UserServiceImpl
   - UserRequest
   - UserController
   - UserService
   - UserDto
   - User
   - UserPrincipal
   - JwtTokenProvider
   - JwtAuthenticationFilter
   - SecurityConfig
   - LoginRequest
   - SignupRequest

UserController
   - UserController
   - UserService
   - AuthController
   - UserRepository
   - CustomUserDetailsService
   - UserServiceImpl
   - UserRequest
   - UserDto
   - User
   - UserPrincipal
   - JwtTokenProvider
   - JwtAuthenticationFilter
   - SecurityConfig
   - LoginRequest
   - SignupRequest
```

---

# ⚠️ Important Current Limitation

The module discovery result is currently **not correct enough for production transformation**.

The algorithm is too heavily influenced by controller roots.

The result duplicates the same business functionality across multiple modules.

This is currently one of the most important problems in ArchMorph.

---

# 📐 Current Module Layout Result

Current output:

```text
Module : AuthController

  CONTROLLER
      - AuthController
      - UserController

  REPOSITORY
      - UserRepository

  SERVICE
      - CustomUserDetailsService
      - UserServiceImpl
      - UserService

  DTO
      - UserRequest
      - UserDto
      - LoginRequest
      - SignupRequest

  ENTITY
      - User

  COMMON
      - UserPrincipal

  COMPONENT
      - JwtTokenProvider
      - JwtAuthenticationFilter

  CONFIGURATION
      - SecurityConfig
```

And similarly:

```text
Module : UserController
```

contains nearly the same classes.

This is exactly why module discovery must be fixed before transformation can be considered reliable.

---

# 🔄 Current Transformation Result

ArchMorph currently generates mappings such as:

```text
Class           : AuthController
Module          : AuthController
Folder          : CONTROLLER

Target Package:
com.pmj.template.authcontroller.controller
```

and:

```text
Class           : UserRepository
Module          : AuthController
Folder          : REPOSITORY

Target Package:
com.pmj.template.authcontroller.repository
```

and:

```text
Class           : UserServiceImpl
Module          : AuthController
Folder          : SERVICE

Target Package:
com.pmj.template.authcontroller.service
```

The transformation engine is therefore operational, but its quality depends heavily on module discovery.

---

# 🚨 Why Transformation Is Not Production Ready Yet

The fundamental issue is:

```text
Incorrect Module Discovery
          ↓
Incorrect Module Layout
          ↓
Incorrect Transformation Mapping
          ↓
Duplicated Classes
          ↓
Potentially Invalid Architecture
```

Therefore:

> **Transformation must not be treated as reliable until module discovery and validation are significantly improved.**

---

# 🛠️ Sprint History / What Has Already Been Solved

ArchMorph is being developed incrementally.

The development can be understood as the following sprint progression.

---

# Sprint 1 — Project Foundation

## Objective

Create the Spring Boot application and basic project infrastructure.

### Implemented

* Spring Boot application
* Maven build
* Maven wrapper
* application configuration
* workspace properties
* common constants
* standardized API response
* global exception handling

### Main concepts

* Spring Boot
* Dependency Injection
* Configuration Management
* REST API design
* Exception Handling

### Result

ArchMorph could start as a backend application and provide a foundation for project processing.

---

# Sprint 2 — Project Upload & Workspace Management

## Objective

Allow users to provide an existing Java project.

### Implemented

* upload endpoint
* upload DTO
* uploaded project model
* archive storage
* ZIP validation
* ZIP extraction
* workspace manager
* project ID generation
* project validation

Pipeline:

```text
ZIP
 ↓
Validation
 ↓
Storage
 ↓
Workspace
 ↓
Extraction
```

### Important design principle

Each uploaded project receives an isolated workspace:

```text
workspace/
└── projects/
    └── PROJECT-XXXX/
        ├── original/
        └── transformed/
```

This prevents different analysis runs from interfering with one another.

---

# Sprint 3 — Java Source Discovery

## Objective

Find Java source files inside arbitrary extracted projects.

### Implemented

* source scanner
* project structure detector
* Java source discovery
* project root detection

### Concepts

* filesystem traversal
* recursive directory scanning
* Maven project structure
* source-root detection

---

# Sprint 4 — Java Parsing

## Objective

Understand Java source code semantically.

### Implemented

* JavaParser integration
* class metadata
* annotation detection
* source parsing
* component metadata

### Concepts

* AST
* static analysis
* Java annotations
* source metadata
* syntax tree traversal

Pipeline:

```text
.java
 ↓
JavaParser
 ↓
AST
 ↓
ClassMetadata
```

---

# Sprint 5 — Component Classification

## Objective

Determine what each class represents.

### Implemented

Current categories:

```text
CONTROLLER
SERVICE
REPOSITORY
ENTITY
DTO
COMPONENT
CONFIGURATION
APPLICATION
EXCEPTION
EXCEPTION_HANDLER
UNKNOWN
```

### Concepts

* Spring stereotypes
* JPA annotations
* naming heuristics
* architectural roles
* semantic classification

---

# Sprint 6 — Project Class Registry

## Objective

Create a centralized representation of all discovered project classes.

### Implemented

* ProjectClassRegistry
* ProjectClassInfo
* ProjectClassCollector
* DefaultProjectClassRegistry

### Why this exists

Multiple parts of ArchMorph need the same project metadata.

Instead of repeatedly scanning source files:

```text
Parser
   ↓
Registry
   ↓
Architecture
Dependency
Module
Transformation
```

---

# Sprint 7 — Dependency Graph

## Objective

Represent relationships between Java classes.

### Implemented

* dependency nodes
* dependency edges
* dependency graph
* dependency node factory
* graph builder
* dependency extractors
* graph traversal

### Concepts

* directed graphs
* nodes
* edges
* graph traversal
* dependency relationships

Current dependency types include:

```text
FIELD
METHOD_PARAMETER
```

with more planned.

---

# Sprint 8 — Architecture Analysis

## Objective

Detect violations in the existing architecture.

### Implemented

* architecture analyzer
* architecture report
* layer analyzer
* layer violation detector
* layer rules
* rule registry
* default layer rules

Example:

```text
Controller → Repository
```

is detected as a violation.

---

# Sprint 9 — Circular Dependency Detection

## Objective

Detect dependency cycles.

### Implemented

* cycle detector
* cycle report
* default cycle detector

Concepts:

* DFS
* directed graph
* cycle detection
* graph traversal

---

# Sprint 10 — Architecture Diagram Generation

## Objective

Represent architecture visually.

### Implemented

* diagram generator abstraction
* diagram report
* PlantUML generator

Future:

* interactive diagrams
* module graphs
* architecture violation visualization

---

# Sprint 11 — Module Discovery

## Objective

Infer business modules.

### Implemented

* module classifier
* module discovery engine
* business module extractor
* naming heuristic classifier
* connected component finder
* module optimizer
* module naming strategy

### Current state

This subsystem is **functional but experimental**.

The current controller-centric discovery is one of the primary areas requiring improvement.

---

# Sprint 12 — Transformation Planning

## Objective

Plan how classes should move into target modules.

### Implemented

* transformation planner
* transformation plan
* transformation plan entries
* package planner
* base package resolver
* module layout planner
* folder classifier
* transformation mappings

Pipeline:

```text
Module
 ↓
Folder
 ↓
Package
 ↓
Target File
```

---

# Sprint 13 — Transformation Engine

## Objective

Actually generate the transformed project.

### Implemented

* transformation engine
* target layout generation
* transformation mapping engine
* transformation reports
* target workspace generation

### Current state

**Experimental.**

The engine requires stronger validation and better module assignments before it can safely transform arbitrary projects.

---

# Sprint 14 — Testing & Reliability

Current testing infrastructure exists, but this is an area that needs significant expansion.

The next major testing strategy is to introduce project fixtures.

Example:

```text
src/test/resources/projects/

├── layered-architecture/
├── circular-dependencies/
├── multiple-domains/
├── security-heavy/
├── modular-monolith/
└── malformed-project/
```

---

# 🐛 Open Issues

The following issues represent the main development roadmap.

---

## ARCH-001 — Improve Dependency Extraction

**Priority: Critical**

Add:

```text
CONSTRUCTOR
METHOD_INVOCATION
RETURN_TYPE
EXTENDS
IMPLEMENTS
ANNOTATION
GENERIC_TYPE
THROWS
STATIC_REFERENCE
```

Add source locations and dependency weights.

---

## ARCH-002 — Improve Component Classification

Improve:

```text
UNKNOWN
```

classification.

Add:

```text
SECURITY
SECURITY_FILTER
SECURITY_PROVIDER
SECURITY_MODEL
SECURITY_CONFIGURATION
CONSTANTS
INFRASTRUCTURE
```

---

## ARCH-003 — Improve Architecture Violation Reports

Include:

* rule
* source
* target
* dependency type
* severity
* location
* explanation
* recommendation

---

## ARCH-004 — Redesign Module Discovery

**Priority: Critical**

Controllers must not automatically become business modules.

Use:

* domain entities
* repositories
* service relationships
* graph connectivity
* naming
* package structure
* cohesion
* coupling

---

## ARCH-005 — Prevent Duplicate Class Assignment

Guarantee:

```text
One source class
       ↓
One primary target module
       ↓
One target file
```

---

## ARCH-006 — Module Cohesion Analysis

Calculate:

```text
internal relationships
external relationships
cohesion score
coupling score
```

---

## ARCH-007 — Module Confidence Score

Explain why each class was assigned to a module.

---

## ARCH-008 — Shared Infrastructure Detection

Detect:

```text
security
configuration
exception handling
logging
common utilities
```

that should not be copied into every business module.

---

## ARCH-009 — Package Rewriting

Safely rewrite:

```text
package declarations
imports
fully qualified references
```

---

## ARCH-010 — Transformation Validation

Before accepting a transformation:

```text
No duplicate classes
No unresolved imports
No missing classes
No invalid packages
No unresolved dependencies
```

---

## ARCH-011 — Compile Transformed Project

Run:

```bash
mvn test
```

on the generated project.

---

## ARCH-012 — Transformation Rollback

Failed transformation must not leave partially generated output.

---

## ARCH-013 — Architecture Profiles

Support:

```text
layered
clean
hexagonal
onion
modular-monolith
custom
```

---

## ARCH-014 — Custom Architecture Rules

Allow users to define rules in YAML/JSON.

---

## ARCH-015 — Configuration Analysis

Analyze:

```text
application.properties
application.yml
profiles
logging
environment variables
external dependencies
```

---

## ARCH-016 — Test Analysis

Map:

```text
Production class
      ↓
Test class
```

and identify important untested components.

---

## ARCH-017 — Golden Project Fixtures

Add real-world-style projects to regression-test ArchMorph.

---

## ARCH-018 — Architecture Quality Score

Calculate:

```text
Layering
Modularity
Coupling
Cohesion
Cycles
Testability
```

---

## ARCH-019 — Architecture Diff

Compare:

```text
Before
vs
After
```

and report:

* moved classes
* new dependencies
* removed dependencies
* resolved violations
* new violations
* changed modules

---

## ARCH-020 — CLI

Provide:

```bash
archmorph analyze
archmorph transform
archmorph validate
archmorph report
```

---

## ARCH-021 — CI Architecture Gate

Allow GitHub Actions / CI systems to fail a build when architectural violations increase.

---

# 🏆 Target End State

A successful ArchMorph transformation should look like:

```text
                    EXISTING PROJECT
                           │
                           ▼
                    SOURCE ANALYSIS
                           │
                           ▼
                 ARCHITECTURE MODEL
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
      DEPENDENCY        LAYER            DOMAIN
        GRAPH          ANALYSIS         DISCOVERY
          │                │                │
          └────────────────┼────────────────┘
                           ▼
                    MODULE MODEL
                           │
                           ▼
                 COHESION / COUPLING
                           │
                           ▼
                TRANSFORMATION PLAN
                           │
                           ▼
                  SOURCE TRANSFORMATION
                           │
                           ▼
                   GENERATED PROJECT
                           │
                           ▼
                    COMPILATION
                           │
                           ▼
                       TESTS
                           │
                           ▼
                 ARCHITECTURE VALIDATION
                           │
                           ▼
                  MODULAR MONOLITH
```

---

# 🔐 Transformation Safety Requirements

Before ArchMorph can claim that a transformation is successful, it should eventually guarantee:

### 1. Compilation

The generated project compiles.

### 2. Test Preservation

Existing tests continue to execute.

### 3. Dependency Preservation

Required dependencies remain available.

### 4. Import Correctness

All imports are rewritten correctly.

### 5. Package Correctness

Package declarations match filesystem structure.

### 6. No Duplicate Classes

Unless explicitly intended.

### 7. No Broken References

All source references resolve.

### 8. Architecture Improvement

The generated project should actually improve the selected architectural metric.

---

# 📦 Example Target Project

A successful modular-monolith transformation could produce:

```text
src/main/java/com/example/application/

├── Application.java
│
├── modules/
│   │
│   ├── user/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   │
│   ├── authentication/
│   │   ├── controller/
│   │   ├── service/
│   │   └── dto/
│   │
│   └── order/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
└── shared/
    ├── configuration/
    ├── security/
    ├── exception/
    └── infrastructure/
```

The exact structure must be determined by the analysis engine.

---

# 🧪 Testing Philosophy

ArchMorph should not be tested only with individual classes.

The most important tests should operate on **complete example projects**.

Example:

```text
Input Project
      ↓
ArchMorph
      ↓
Expected Architecture Model
      ↓
Actual Architecture Model
      ↓
Compare
```

This allows regression testing of the entire pipeline.

---

# 📁 Recommended Test Fixtures

```text
src/test/resources/projects/

├── layered-basic/
├── layered-security/
├── circular-dependency/
├── multiple-business-domains/
├── shared-infrastructure/
├── high-coupling/
├── clean-architecture/
├── hexagonal-architecture/
└── modular-monolith/
```

Each fixture should contain:

```text
source project
expected component classification
expected dependency graph
expected architecture violations
expected modules
expected transformation
```

---

# 👨‍💻 Contributor Guide

You do not need to understand the whole project before contributing.

Choose one subsystem.

---

## Parser Contributor

Work in:

```text
parser/
```

Good tasks:

* improve annotation detection
* add class metadata
* improve Java parsing
* add generic type detection

---

## Dependency Contributor

Work in:

```text
analysis/dependency/
```

Good tasks:

* constructor dependencies
* method invocation dependencies
* inheritance
* annotations
* return types
* generic types

---

## Graph Contributor

Work in:

```text
graph/
analysis/module/graph/
```

Good tasks:

* DFS
* BFS
* connected components
* strongly connected components
* graph optimization

---

## Architecture Contributor

Work in:

```text
analysis/architecture/
```

Good tasks:

* new architecture rules
* violation severity
* architecture profiles
* architecture reports

---

## Module Discovery Contributor

Work in:

```text
analysis/module/
```

This is one of the highest-impact areas.

The goal is:

```text
Controller
    ↓
Business Capability
```

rather than:

```text
Controller
    ↓
Module
```

---

## Transformation Contributor

Work in:

```text
analysis/transformation/
```

Focus on:

* package rewriting
* import rewriting
* file movement
* duplicate prevention
* dependency preservation
* compilation validation

---

#  Good First Issues

Good first contributions include:

* add a missing component classification
* add a unit test
* improve error messages
* improve documentation
* add a dependency extractor test
* add a PlantUML test
* create a new project fixture
* improve logging
* improve README examples

---

#  Advanced Contributions

Advanced contributors can work on:

* graph-based domain discovery
* module clustering
* cohesion/coupling metrics
* source transformation
* AST rewriting
* architecture optimization
* automated refactoring
* architecture diff
* CI architecture gates

---

# 🌿 Branch Naming

Recommended:

```text
feature/module-discovery
feature/dependency-extractor
feature/transformation-validation

fix/duplicate-module-assignment
fix/component-classification

test/layered-project-fixture
test/module-discovery

docs/contributing-guide
```

---

# 📝 Commit Convention

Use conventional commits.

Examples:

```text
feat: add constructor dependency extraction

fix: prevent duplicate class assignment

test: add layered architecture fixture

refactor: simplify module discovery

docs: improve transformation documentation

perf: optimize dependency graph traversal

build: update JavaParser dependency
```

---

# 🔀 Pull Request Requirements

A Pull Request should contain:

## Problem

What problem does the PR solve?

## Approach

How was it solved?

## Tests

What tests were added?

## Before

What did ArchMorph produce before?

## After

What does ArchMorph produce now?

## Limitations

What is still not solved?

---

# 📋 Pull Request Checklist

```text
[ ] Code compiles
[ ] Existing tests pass
[ ] New tests added
[ ] No unrelated changes
[ ] Documentation updated if required
[ ] No generated workspace committed
[ ] No secrets committed
[ ] Transformation output validated
[ ] Architecture behavior explained
```

---

# 🔬 Contribution Philosophy

ArchMorph is not intended to become a collection of hard-coded rules.

The goal is to build a general architecture reasoning pipeline.

Bad:

```text
if className.equals("UserController")
    module = "User";
```

Better:

```text
Domain Evidence
+
Dependency Evidence
+
Structural Evidence
+
Semantic Evidence
+
Naming Evidence
=
Module Assignment
```

The architecture engine should be explainable.

---

# 🧠 Explainability

For every important automated decision, ArchMorph should eventually answer:

> Why did you make this decision?

Example:

```text
UserRepository → User Module

Evidence:

@Entity relationship        30%
Repository relationship     25%
Service relationship        20%
Naming similarity            10%
Package similarity            5%
Graph connectivity            10%

Confidence: 92%
```

This is critical for user trust.

---

# 🗺️ Roadmap

## Phase 1 — Foundation

* [x] Spring Boot application
* [x] Maven build
* [x] Project upload
* [x] ZIP validation
* [x] Workspace management
* [x] Java source scanning
* [x] Java parsing

---

## Phase 2 — Architecture Understanding

* [x] Component classification
* [x] Project class registry
* [x] Dependency graph
* [x] Graph traversal
* [x] Layer analysis
* [x] Architecture rules
* [x] Violation detection
* [x] Cycle detection
* [x] PlantUML generation

---

## Phase 3 — Business Understanding

* [x] Basic module discovery
* [x] Module classifier
* [x] Module extractor
* [x] Naming heuristics
* [x] Connected components

### Remaining

* [ ] Domain-aware discovery
* [ ] Cohesion
* [ ] Coupling
* [ ] Confidence scoring
* [ ] Shared infrastructure
* [ ] Module boundary validation

---

## Phase 4 — Transformation

* [x] Transformation planner
* [x] Transformation plan
* [x] Package planner
* [x] Layout planner
* [x] Transformation mappings
* [x] Transformation engine

### Remaining

* [ ] Package rewriting
* [ ] Import rewriting
* [ ] AST transformation
* [ ] Duplicate prevention
* [ ] Dependency preservation
* [ ] Compilation validation
* [ ] Test validation
* [ ] Rollback

---

## Phase 5 — Architecture Intelligence

* [ ] Architecture profiles
* [ ] Custom rules
* [ ] Cohesion metrics
* [ ] Coupling metrics
* [ ] Architecture score
* [ ] Confidence scores
* [ ] Refactoring recommendations
* [ ] Architecture diff

---

## Phase 6 — Developer Tooling

* [ ] CLI
* [ ] Interactive visualization
* [ ] GitHub Actions
* [ ] CI architecture gates
* [ ] PR comments
* [ ] Architecture history
* [ ] Architecture drift detection

---

# 📈 Definition of a Successful Transformation

ArchMorph should eventually consider a transformation successful only when:

```text
Source project analyzed
        +
Architecture understood
        +
Modules discovered
        +
Transformation planned
        +
Classes transformed
        +
Packages rewritten
        +
Imports rewritten
        +
No duplicates
        +
No broken references
        +
Project compiles
        +
Tests pass
        +
Architecture improves
```

Only then should the system produce:

```text
transformed-project.zip
```

as a successful modular-monolith transformation.

---

# 🏁 Final Vision

ArchMorph ultimately aims to become:

```text
          "Architecture Compiler"
```

for Java applications.

Similar to how a compiler transforms:

```text
Source Code
    ↓
AST
    ↓
Intermediate Representation
    ↓
Optimizations
    ↓
Target Code
```

ArchMorph aims to transform:

```text
Java Project
    ↓
AST
    ↓
Architecture Model
    ↓
Dependency Graph
    ↓
Domain Model
    ↓
Architecture Optimization
    ↓
Transformation Plan
    ↓
Target Architecture
    ↓
Validated Java Project
```

The ultimate goal is:

> **Take an existing Java application, understand its architecture automatically, identify meaningful business boundaries, and safely restructure it into a maintainable modular monolith.**

---

# ⭐ Why Contribute?

ArchMorph combines several challenging areas:

* Java
* Spring Boot
* JavaParser
* Static analysis
* AST processing
* Graph algorithms
* Software architecture
* Domain discovery
* Dependency analysis
* Program transformation
* Automated refactoring
* Modular monoliths
* Architecture governance
* Developer tooling

This makes the project suitable for contributors interested in both **software engineering and research-oriented problems**.

---

# ⚠️ Current Limitations

ArchMorph is not yet guaranteed to correctly transform arbitrary production applications.

Current limitations include:

1. Dependency extraction is incomplete.
2. Some classes are classified as UNKNOWN.
3. Module discovery can be controller-centric.
4. Shared infrastructure detection is incomplete.
5. Classes may currently be assigned to multiple modules.
6. Transformation validation is incomplete.
7. Package/import rewriting requires further development.
8. Generated projects are not yet automatically guaranteed to compile.
9. Existing tests are not yet automatically validated after transformation.
10. Architecture profiles are limited.
11. Configuration analysis is limited.
12. Test analysis is limited.

These are tracked as GitHub issues and are intended to be solved incrementally.

---

# 🤝 Open Source

ArchMorph is intentionally being developed as an open-source project.

Contributors are encouraged to:

* challenge architectural assumptions
* propose better algorithms
* add test projects
* identify incorrect analysis
* improve transformation safety
* add architecture strategies
* improve documentation
* build tooling around the analysis engine

A contributor does not need to agree with the current implementation.

In fact:

> **Finding an incorrect architectural assumption is a valuable contribution.**

---

# 📜 License

MIT License.

See `LICENSE`.

---

# 🔗 Repository

GitHub:

[https://github.com/Anvith433/archmorph](https://github.com/Anvith433/archmorph)

---

# 🙌 Contributors

Built as an open-source project with the goal of making automated Java architecture analysis and restructuring accessible to developers.

**Understand the architecture.**

**Find the problems.**

**Discover the boundaries.**

**Transform safely.**

**Build better software.**

````

### The important part

I would use this README as the **technical baseline**, but I would also create a separate `CONTRIBUTING.md` later. The README explains the project; `CONTRIBUTING.md` should explain the contributor's exact development workflow.

For your repository specifically, the **next major milestone should not be "add more features."** It should be:

```text
CURRENT
24 classes
↓
20 detected dependencies
↓
2 controller-driven modules
↓
duplicated transformation

                    ↓

MILESTONE 1
24 classes
↓
complete semantic dependency graph
↓
correct business-module discovery
↓
single ownership per class
↓
validated target module structure
↓
compiles
↓
tests pass
````
