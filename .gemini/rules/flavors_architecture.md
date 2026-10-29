# DEVBOX ARCHITECTURE LAWS (ABSOLUTE & PERMANENT)

See `/home/coder/projects/devbox/ARCHITECTURE_RULES.md` for the complete ruleset.

## Key Rules
1. **Single Source of Truth**: 99% of code remains in `src/main/`. ZERO UI in flavor folders.
2. **Pluggable Linux Backend**: Single interface `LinuxBackend` in `src/main`. `ChrootBackend` in `src/root`, `PRootBackend` in `src/noroot`.
3. **Single Manifest**: 1 `AndroidManifest.xml` in `src/main`.
4. **ARM64 Only**: `arm64-v8a` only. No 32-bit or x86.
5. **Zero Hardcode**: Standard ANSI/VT100 escape sequence protocols only.
6. **Zero Emoji**: Clean technical English everywhere.
7. **Zero Local Builds**: GitHub Actions CI with `--interval 60`.
