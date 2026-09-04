# Contributing to Sohva TV

Thank you for helping improve Sohva TV. Pull requests should stay focused,
buildable, and safe for an Android TV living-room device.

## Before submitting

1. Use JDK 17 and Android SDK platform 36.
2. Run:

   ```powershell
   .\gradlew.bat testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease --warning-mode=fail
   ```

3. Test D-pad focus, Back behavior, and screen restoration for UI changes.
4. Keep provider calls bounded, cancellable, and off the main thread.
5. Add or update tests for behavior changes.

Never submit real playlist URLs, credentials, exported backups, viewing data,
device addresses, provider dumps, or copyrighted channel and sports assets.
Fixtures must use fictional data, reserved example domains, and documentation
address ranges.

The legacy `com.streammate.tv` package and internal identifiers are an update-
compatibility boundary. Do not rename them as cosmetic cleanup.

By contributing, you agree that your contribution is licensed under
`GPL-3.0-only`, the same licence as Sohva TV's original source.
