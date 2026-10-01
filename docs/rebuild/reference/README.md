# Reference material from Sohva TV beta 23

Originally copied from the public beta 23 tree (`efab52a`) on 24 September 2026.
French resources and its picker label were added for the rebuild on 30 September 2026;
French was not in beta 23. Existing languages retain their original text except for
newly implemented features and the updated list of draft translations.

## strings/

Every string resource of the app, in all seven languages, laid out by the old module that owned
it: `app`, `core`, `iptv`, `sportmate`, plus the `demo` and `lab` build flavours' app labels.
About 9,900 `<string>` and `<plurals>` entries in total.

| Language | Folder qualifier | Status in the app |
|---|---|---|
| English | `values` | Complete, the fallback |
| French | `values-fr` | Complete coverage, draft wording; newly translated for the rebuild at the owner’s request on 30 September 2026 |
| Finnish | `values-fi` | Complete (the owner's language; also has the Trakt strings) |
| Spanish, Portuguese, German, Swedish, Italian | `values-es`, `-pt`, `-de`, `-sv`, `-it` | Drafts; the Trakt strings fall back to English |

`app/values/strings_addon_entry.xml` holds one untranslatable name ("Discover"), so it exists in
English only by design.

Use them as the text source for the rebuild: the visible texts should stay the same in every
language, even if the keys are renamed or the files are reorganised by feature. See
[specs/74-localization.md](../specs/74-localization.md) for the rules.

## tester-docs-beta23/

The documents that ship with the public beta and its source: README, INSTALL, TESTING (the
tester checklist, useful as a manual acceptance list), RELEASE_NOTES, ADDONS (Discover setup),
PRIVACY (the privacy commitments the rebuild must keep), THIRD_PARTY_NOTICES, SECURITY,
CONTRIBUTING and the GPL-3.0 LICENSE.
