# Security policy

Sohva TV keeps IPTV and service credentials on the TV, encrypted with an Android Keystore key, and
handles stream addresses that may contain account details. If you find a way these can leak, please
report it privately to [hello@luontra.fi](mailto:hello@luontra.fi). That includes:

- a credential or stream address shown on screen, written to a log or to a diagnostics file;
- a weakness in the encrypted backup (`.smbak`);
- an exported component or media-session connection another app can misuse;
- anything else that exposes what you entered into the app.

Say which Sohva TV version and Android TV version you use and how to reproduce the problem. Do not
send working provider credentials, playlist files, backups or unrelated device data; replace
sensitive values with fictional ones (for example `provider.example`).

Only the latest numbered beta is supported. This is a personal project, so there is no guaranteed
response time, but credible reports are reviewed before anything is made public.
