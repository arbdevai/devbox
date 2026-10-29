# DEVBOX ARCHITECTURE LAWS (ABSOLUTE & PERMANENT)

> [!IMPORTANT]
> This document defines the non-negotiable architectural laws of DevBox.
> All developers, automated systems, and AI assistants (across any conversation session) must strictly comply with these rules. No exceptions.

---

## 1. The Single Source of Truth Law (Zero UI in Flavors)
* **99% of code MUST reside in `src/main/`**:
  - All Jetpack Compose UI (`TerminalScreen`, `TerminalCanvas`, `DashboardScreen`, `WebPreviewScreen`, `WebIdeScreen`).
  - Native C engine (`libvterm`, `devbox_vterm_jni.c`, `NativeVTerm.kt`).
  - Terminal grid buffers, DEC 1049 alternate screen state, DEC 2026 atomic frame synchronization.
  - Session manager, tab bars, themes, fonts, and settings.
* **STRICT PROHIBITION**:
  - Never place UI components, composables, or terminal buffer code inside `src/root/` or `src/noroot/`.
  - Flavor folders must remain ultra-thin, containing solely the backend process launcher implementation.

---

## 2. The Pluggable Linux Backend Law (Single Interface)
* There is exactly **ONE** backend abstraction interface in `src/main/`:
  - `com.arbdev.devbox.backend.LinuxBackend`
* Each flavor implements this single interface:
  - **Flavor `root`** (`src/root/`):
    - Implements `ChrootBackend` using native Linux `chroot`, `/dev/pts`, and `su` (via `libsu`).
    - Operates at native kernel speed with zero overhead.
  - **Flavor `noroot`** (`src/noroot/`):
    - Implements `PRootBackend` using static `proot` userspace syscall interception in `context.filesDir`.
    - Operates without root permissions.
* Flavor-specific dependencies must be isolated:
  - `rootImplementation` for `libsu`.
  - `norootImplementation` for any PRoot asset unpackers.

---

## 3. The Single Manifest Law (Anti-Merge Collision)
* Maintain exactly **ONE** `AndroidManifest.xml` in `src/main/`.
* Do NOT create separate `AndroidManifest.xml` files inside `src/root/` or `src/noroot/` unless strictly required for different package names or metadata.
* This guarantees that Android Manifest Merging never encounters attribute collisions, duplicated activities, or permission conflicts.

---

## 4. The 64-Bit ARM Law (Modern Ecosystem Guarantee)
* Target architecture is strictly **64-bit ARM**:
  ```kotlin
  ndk {
      abiFilters += listOf("arm64-v8a")
  }
  ```
* Do not dilute the codebase or CI resources with 32-bit ARM (`armeabi-v7a`) or x86 emulators.
* Modern AI agent CLIs (`agy`, Node.js 20+, Claude Code, Python wheels) require a 64-bit architecture to function reliably without Out-Of-Memory (OOM) failures or missing prebuilt C++ bindings.

---

## 5. The Zero Hardcode Law
* Never hardcode process names, agent CLI commands, or shell prompts (e.g., no `if (cmd == "agy")` or `if (app == "claude")`).
* Terminal buffer switching, alternate screen detection, cursor handling, and mouse protocols MUST strictly rely on ANSI/VT100 escape sequence standards (`DEC Mode 1049`, `DEC Mode 2026`, `DEC 1006 SGR`, `DECCKM`).

---

## 6. The Zero Emoji & Professionalism Law
* All commit messages, GitHub release notes, UI text, logs, and changelogs MUST follow a **Zero-Emoji Policy**.
* Technical documentation and release changelogs must use clean, professional technical English.

---

## 7. The Build & CI Discipline Law
* **Zero Local Builds**: Never run `./gradlew` locally on developer workstations or containers.
* All APK compilations and release artifacts are built autonomously via GitHub Actions CI/CD.
* CI monitoring interval must strictly be set to 60 seconds (`--interval 60`) to avoid API rate-limiting.
* Versioning follows **Semantic Versioning 2.0.0 (SemVer)**: `MAJOR.MINOR.PATCH` with incremental 5-digit `versionCode` (e.g., `1.1.2` -> `10102`, `1.2.0` -> `10200`).
