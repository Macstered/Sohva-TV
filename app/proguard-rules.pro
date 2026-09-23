# Release builds are shrunk and optimised by R8 (never before 23 September
# 2026, when a slow-box investigation found the release carrying 39 MB of dex
# and its two largest composables past the size ART will compile ahead of
# time). The app reads its JSON by hand and loads nothing by reflection; the
# libraries bring their own rules.

# The project is public: renaming classes protects nothing and would make
# testers' diagnostics, stack traces and system traces unreadable.
-dontobfuscate

# Line numbers in the stack traces testers send. R8 still merges classes, so
# a frame can name a class the method did not start in; the mapping file
# kept with each release says where it came from.
-keepattributes SourceFile,LineNumberTable
