# Sohva TV rebuild

@AGENTS.md

## Notes for Claude Code

- The kit in `docs/rebuild/` is the specification. Start every task by finding the feature IDs it
  touches in `docs/rebuild/plan/01-feature-inventory.md` and reading those specs; do not work from
  memory of the old app. Decisions go in `docs/decisions.md`, measurements in
  `docs/performance-log.md`.
- The old app is read-only reference material at
  `G:\SportMate\.local\sohva-sport-user-reports\.local\beta19-public-source`. Never edit,
  build, commit or push there.
- The machine is Windows 11 with Git Bash and Windows PowerShell 5.1 (no PowerShell 7). Use
  absolute paths; the shell's working directory resets between commands. Send long Gradle runs to
  a log file (`> build.log 2>&1`), never through a pipe: the Gradle daemon holds a pipe open and
  the command never returns.
- Write longer Python helper scripts to a file and run them; long heredocs with quotes and
  backslashes fail to parse. A `\uXXXX` escape typed into a file arrives as the character itself.
- Device logs from the emulator scripts are UTF-16; strip NULs before grepping them.
- Ask before anything outward-facing: pushing, releasing, installing on a real device.
