# Contributing to Hazel

Thank you for your interest in contributing to Hazel! Whether it's a bug fix, new feature, or documentation improvement — every contribution makes a difference.

Please read this guide carefully before submitting your first pull request.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Fork & Branch Workflow](#fork--branch-workflow)
- [Making Changes](#making-changes)
- [Commit Message Format](#commit-message-format)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)
- [What We Accept](#what-we-accept)
- [What We Won't Accept](#what-we-wont-accept)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold its standards. Report unacceptable behavior via [GitHub Issues](https://github.com/SibtainOcn/Hazel/issues).

---

## Getting Started

### First-Time Contributors

1. Look for issues labeled [`good first issue`](https://github.com/SibtainOcn/Hazel/labels/good%20first%20issue) — these are beginner-friendly tasks
2. Comment on the issue to let others know you're working on it
3. Follow the [Fork & Branch Workflow](#fork--branch-workflow) below

### Reporting Bugs

Use the [Bug Report](https://github.com/SibtainOcn/Hazel/issues/new?template=bug_report.yml) template. Include:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots or screen recordings (drag & drop into the form)
- Hazel version (Settings → About)

### Suggesting Features

Use the [Feature Request](https://github.com/SibtainOcn/Hazel/issues/new?template=feature_request.yml) template. Describe the problem you're solving, not just the feature you want.

---

## Development Setup

### Prerequisites

| Tool | Version | Required |
|------|---------|----------|
| Android Studio | Latest stable | ✅ |
| JDK | 17 | ✅ |
| Android SDK | API 35+ | ✅ |
| Git | Any | ✅ |
| Physical device or emulator | Android 7.0+ | ✅ |

### Initial Setup

```bash
# 1. Fork the repo on GitHub (click the "Fork" button)
# 2. Clone YOUR fork (not the original repo)
git clone https://github.com/YOUR_USERNAME/Hazel.git
cd Hazel

# 3. Add the original repo as "upstream" remote
git remote add upstream https://github.com/SibtainOcn/Hazel.git

# 4. Verify remotes
git remote -v
# origin    https://github.com/YOUR_USERNAME/Hazel.git (fetch)
# origin    https://github.com/YOUR_USERNAME/Hazel.git (push)
# upstream  https://github.com/SibtainOcn/Hazel.git (fetch)
# upstream  https://github.com/SibtainOcn/Hazel.git (push)
```

Open the project in Android Studio — Gradle will sync automatically.

### Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Run lint checks
./gradlew lint
```

### Signing (for release builds only)

Contributors do **not** need the official signing key. Create your own for testing:

```bash
keytool -genkey -v -keystore my-debug-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias key0
```

Create a `signing.properties` file in any directory:
```properties
storeFile=my-debug-key.jks
storePassword=your_password
keyAlias=key0
keyPassword=your_password
```

Add to `local.properties` (already gitignored):
```properties
HAZEL_SIGNING_DIR=/path/to/directory/containing/signing.properties
```

---

## Fork & Branch Workflow

> **We use the Fork & Pull Request model** — the industry standard for open-source projects. You do NOT push directly to this repo.

### Step 1: Fork

Click the **"Fork"** button on the top-right of the [Hazel repository](https://github.com/SibtainOcn/Hazel). This creates your own copy under `github.com/YOUR_USERNAME/Hazel`.

### Step 2: Keep Your Fork in Sync

Before starting any work, always sync with the latest upstream:

```bash
# Fetch latest changes from the original repo
git fetch upstream

# Switch to your main branch
git checkout main

# Merge upstream changes into your main
git merge upstream/main

# Push the updated main to your fork
git push origin main
```

> ⚠️ **Never work directly on `main`**. Always create a feature branch.

### Step 3: Create a Feature Branch

```bash
# Create and switch to a new branch
git checkout -b feat/your-feature-name

# Branch naming convention:
# feat/description    — for new features
# fix/description     — for bug fixes
# docs/description    — for documentation
# refactor/description — for code restructuring
```

### Step 4: Make Your Changes

- Write clean, well-documented code
- Follow the [Code Style](#code-style) guidelines
- Test on a real device or emulator
- Run `./gradlew lint` — fix any warnings

### Step 5: Commit

```bash
git add .
git commit -m "feat: add playlist shuffle mode"
```

See [Commit Message Format](#commit-message-format) for rules.

### Step 6: Push to Your Fork

```bash
git push origin feat/your-feature-name
```

### Step 7: Open a Pull Request

1. Go to your fork on GitHub
2. Click **"Compare & pull request"**
3. Set base repository to `SibtainOcn/Hazel` → base branch `main`
4. Fill in the PR template with:
   - Description of changes
   - Type of change (bug fix, feature, etc.)
   - Screenshots for UI changes
   - Related issue number (`Fixes #123`)
5. Submit the PR

### Step 8: Code Review

- A maintainer will review your PR
- Address any requested changes by pushing new commits to the **same branch**
- Once approved, the maintainer will merge your PR

### After Merge: Cleanup

```bash
# Switch back to main
git checkout main

# Sync with upstream
git fetch upstream
git merge upstream/main
git push origin main

# Delete your feature branch locally
git branch -d feat/your-feature-name

# Delete your feature branch on GitHub
git push origin --delete feat/your-feature-name
```

---

## Commit Message Format

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short description>

[optional body]
[optional footer]
```

### Types

| Type | When to Use |
|------|-------------|
| `feat:` | New feature or functionality |
| `fix:` | Bug fix |
| `docs:` | Documentation only changes |
| `style:` | Formatting (no logic change) |
| `refactor:` | Code restructuring (no behavior change) |
| `perf:` | Performance improvement |
| `test:` | Adding or updating tests |
| `chore:` | Build system, CI, tooling changes |

### Examples

```
feat: add batch download progress notification
fix: crash when playlist URL contains special characters
docs: add build instructions for Linux
refactor: extract download logic into separate repository class
perf: reduce memory usage during large playlist downloads
chore: update Gradle to 9.4
```

### Rules

- Use **lowercase** for the description
- **No period** at the end
- Keep the first line under **72 characters**
- Use the body to explain **what** and **why**, not how
- Reference issues: `Fixes #42` or `Closes #42`

---

## Pull Request Process

1. **One PR = One concern** — don't mix unrelated changes
2. **Update `CHANGELOG.md`** — add your change under `[Unreleased]`
3. **All CI checks must pass** — lint and build
4. **At least one approval** from a maintainer is required
5. **Squash merge** — we squash commits on merge for a clean history
6. **No force-pushing** to `main` — ever

### PR Size Guidelines

| Size | Lines Changed | Review Time |
|------|--------------|-------------|
| Small | < 100 | Hours |
| Medium | 100-500 | 1-2 days |
| Large | 500+ | May require discussion first |

> 💡 **Large changes**: Open an issue first to discuss the approach before writing code. This saves everyone's time.

---

## Code Style

### General

- **Language**: Kotlin only (no Java)
- **UI Framework**: Jetpack Compose + Material 3
- **Architecture**: MVVM (ViewModel + Repository)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35

### Formatting

- **Indentation**: 4 spaces (no tabs)
- **Max line length**: 120 characters
- **Trailing commas**: Yes, for multi-line parameters
- **Blank lines**: One between logical sections, two before class-level members

### Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Classes | PascalCase | `DownloadViewModel` |
| Composables | PascalCase | `UpdateDialog` |
| Functions | camelCase | `startDownload()` |
| Variables | camelCase | `downloadProgress` |
| Constants | SCREAMING_SNAKE | `MAX_RETRY_COUNT` |
| Packages | lowercase | `com.hazel.android.data` |

### Compose Guidelines

- Composables that emit UI = PascalCase (`@Composable fun SettingsScreen()`)
- Composables that return values = camelCase (`@Composable fun rememberPlayerState()`)
- State hoisting — lift state up, pass events down
- Use `remember` and `derivedStateOf` appropriately

### Do's and Don'ts

| ✅ Do | ❌ Don't |
|-------|---------|
| Use `sealed class` for state | Use string constants for state |
| Use `Flow` for async data | Use callbacks/listeners |
| Comment *why* not *what* | Over-comment obvious code |
| Handle errors gracefully | Swallow exceptions silently |
| Use `MaterialTheme` tokens | Hardcode colors/dimensions |

---

## What We Accept

- ✅ Bug fixes with clear reproduction steps
- ✅ Performance improvements with measurements
- ✅ UI/UX improvements following Material 3 guidelines
- ✅ New platform support
- ✅ Accessibility improvements
- ✅ Documentation improvements
- ✅ Test coverage improvements

## What We Won't Accept

- ❌ Changes that break existing functionality without prior discussion
- ❌ Large refactors without an approved issue
- ❌ Code with hardcoded secrets, keys, or credentials
- ❌ Features that violate platform ToS
- ❌ PRs without testing or changelog updates
- ❌ AI-generated code dumps without understanding or testing

---

## Questions?

- **General questions**: [GitHub Discussions](https://github.com/SibtainOcn/Hazel/discussions)
- **Bug reports**: [Issue Tracker](https://github.com/SibtainOcn/Hazel/issues)
- **Security issues**: See [SECURITY.md](SECURITY.md)

Thank you for contributing! 🌿
