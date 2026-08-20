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
Project Upload              █████████████████░░░ 85%
Workspace Management        ██████████████████░░ 90%
Java Source Scanning        ██████████████████░░ 90%
Java Parsing                ██████████████████░░ 90%
Component Classification    ███████████████░░░░░ 75%
Dependency Analysis         ███████████░░░░░░░░░ 55%
Architecture Analysis       ██████████████░░░░░░ 70%
Cycle Detection             █████████████░░░░░░░ 65%
Module Discovery            ██████░░░░░░░░░░░░░░ 30%
Transformation Planning    ████████░░░░░░░░░░░░ 40%
Transformation Execution    █████░░░░░░░░░░░░░░░ 25%
Validation                  ████░░░░░░░░░░░░░░░░ 20%
Testing                     ███████░░░░░░░░░░░░░ 35%
