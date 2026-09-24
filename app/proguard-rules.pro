# The project is public: renaming protects nothing and would make testers' diagnostics, stack
# traces and system traces unreadable. R8 still shrinks, optimises and merges classes, so keep
# each frozen build's mapping.txt for retracing (plan/05 §3.6, §4.6).
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# The app uses no reflection, so it needs no keep rules; libraries bring their own. Any rule added
# here names the failure it prevents and the test that fails without it.
