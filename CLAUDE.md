# CLAUDE.md — Engineering conventions for InvestGuideUA

Guidance for any agent working in this repo. Development and local deployment target **Windows**
(PowerShell 5.1 / 7+, Docker Desktop). See `SPECIFICATION.md` and `TASKS.md` for scope.

## File encoding (MANDATORY — caused a real outage already)

**All scripts executed directly by Windows — `.ps1`, `.cmd`, `.bat` — MUST be pure ASCII.**

Do not use non-ASCII punctuation anywhere in these files (including comments): no em-dash `—`,
en-dash `–`, curly quotes `“ ” ‘ ’`, ellipsis `…`, non-breaking space, `≥`, `§`, etc. Use plain
ASCII (`-`, `"`, `'`, `...`, `>=`).

**Why:** files here are written as UTF-8 **without a BOM**. Windows PowerShell 5.1, seeing no
BOM, decodes the file with the legacy ANSI code page (Windows-1252), not UTF-8. A UTF-8 em-dash
(`E2 80 94`) is then mis-decoded; byte `0x94` becomes `”` (U+201D), which PowerShell treats as a
string delimiter — opening a string that never closes and breaking the whole script with
`missing terminator` / `missing '}'` parser errors. ASCII-only sidesteps this entirely.

If a non-ASCII character is genuinely required in a Windows script, save that file as **UTF-8
with BOM** or UTF-16 — but prefer ASCII.

This rule is about *Windows-executed scripts specifically*. Java sources may use UTF-8 (`§`,
Ukrainian text) because the Maven build pins `project.build.sourceEncoding=UTF-8`; Linux shell
scripts, Dockerfiles and configs are read as UTF-8 by their Linux consumers.

## Line endings

`.gitattributes` pins line endings. Files consumed inside Linux containers (`Dockerfile`,
`*.conf`, `*.yml`, `.env`) stay **LF**; `*.ps1`/`*.cmd`/`*.bat` stay **CRLF**. Do not override.

## When writing `.env` from a script

Write it **without a BOM** (`new System.Text.UTF8Encoding($false)` /
`[System.IO.File]::WriteAllLines`). Windows PowerShell 5.1's `Set-Content -Encoding UTF8` adds a
BOM that can corrupt the first line for Docker Compose's `env_file` parser.

## Verification — do not skip

Reviewing rendered text in chat **cannot** catch encoding bugs: a mis-encoded character looks
normal on screen. The only reliable check is inspecting the file bytes or running a parser.

Before marking any script or source change done:

1. **Scan executed scripts for non-ASCII:** they must come back clean.
   ```bash
   grep -rnP "[^\x00-\x7F]" path/to/*.ps1   # expect: no matches
   ```
2. **Parse/compile when a runtime is available.** PowerShell:
   `pwsh -NoProfile -Command "$null=[System.Management.Automation.Language.Parser]::ParseFile('script.ps1',[ref]$null,[ref]$e); $e"`.
   Java: `mvn -q test`. If the runtime isn't available in the environment, say so explicitly and
   state that verification was static-only — never imply a script "works" when it was only read.
3. Code review by a human or sub-agent is **not** a substitute for (1) and (2) for any
   encoding/parse class of bug.

## Project rules (from project settings)

- Frontend: Angular 17+ (standalone). Backend: Java 21 / Spring Boot 3.x / MongoDB.
- Money is always integer minor units (kopiykas); never float.
- No deprecated/outdated dependencies, libraries, or frameworks.
- Each non-trivial task is reviewed by at least two role sub-agents (QA, DevOps, BE/FE lead,
  etc.) — but per the Verification section above, that review must include an actual
  scan/parse, not just reading.
- This sub-agent review is **mandatory and automatic**: run it as part of completing the task,
  without pausing to ask the user for permission or confirmation first. Spin up the role
  sub-agents, apply or report their findings, and only then consider the task done. Do not offer
  the review as an optional follow-up — it is a required step, not a suggestion.
