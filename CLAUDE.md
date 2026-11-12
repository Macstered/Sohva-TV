# Sohva TV rebuild

@AGENTS.md

## Notes for Claude Code

- The kit in `docs/rebuild/` is the specification. Start every task by finding the feature IDs it
  touches in `docs/rebuild/plan/01-feature-inventory.md` and reading those specs; do not work from
  memory of the old app. Decisions go in `docs/decisions.md`, measurements in
  `docs/performance-log.md`.
- The old app is read-only reference material at
  `/mnt/g/SportMate/.local/sohva-sport-user-reports/.local/beta19-public-source`. Never edit,
  build, commit or push there.
- The machine is Ubuntu Linux with bash (Windows is gone since November 2026). The Android SDK is
  in `~/Android/Sdk` (`ANDROID_HOME`, set in `~/.profile`); the rebuild AVDs `sohva_rebuild_tv30`
  and `sohva_rebuild_tv34` run on KVM. Gradle runs this project on JDK 17
  (`gradle/gradle-daemon-jvm.properties`); the system `java` is 21. `python` is Python 3.
- Use absolute paths; the shell's working directory resets between commands. Send long Gradle runs
  to a log file (`> build.log 2>&1`), never through a pipe: the Gradle daemon holds a pipe open and
  the command never returns. Device tasks need `ANDROID_SERIAL=emulator-NNNN`.
- Write longer Python helper scripts to a file and run them; long heredocs with quotes and
  backslashes fail to parse.
- Ask before anything outward-facing: pushing, releasing, installing on a real device.
