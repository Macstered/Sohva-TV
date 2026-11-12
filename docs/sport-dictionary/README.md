# Reviewed sports dictionary

The first implementation slice contains 43 reviewed API-Sports participants. It covers Finland
and Germany national sides in football and hockey, with separate women/youth/reserve identities,
and seven clubs from the owner's reported matching cases. It includes sourced country labels
in all eight current interface languages and Huuhkajat, Helmarit and Leijonat. Other collected
identities and cross-provider links remain pending; a matching name never approves a link.

`reviewed.json` is a review snapshot, with persistent Sohva UUIDs, accepted provider mappings,
spelling evidence and source notices. It contains no keys, raw responses, logos or match feeds.
The compiler collapses duplicate runtime spellings and retains validity intervals. Known
collisions outside this slice are denial markers for its spellings, not additional aliases.

Builds generate `sport-dictionary.db`, its manifest and credits under ignored build output.
Do not check in a database. Repeated compilation of the same snapshot must produce the same
database bytes and manifest. Run:

```powershell
python tools/compile_sport_dictionary.py --source docs/sport-dictionary/reviewed.json
```

The desktop registry has its own schema version and uses the collection dataset lock. Seeding
assigns pending UUIDs once and leaves jobs, quotas and request pacing unchanged. Review with
`tools/sport_registry.py`; its help lists identity, binding, spelling, credit and membership
operations. Export the next accepted snapshot only after reviewing source and category evidence.

Country labels use Unicode CLDR 48.2.0. The required notice is in `Unicode-3.0.txt` and the
generated credits. Provider name/ID references cite the [API-Sports terms](https://api-sports.io/terms);
these are source terms, not an open-data licence. Federation references support factual nicknames;
their article text and images are excluded. Public distribution remains an owner release decision.

Alias and membership dates are inclusive UTC fixture dates. Unmapped explicit references do
not fall back to a different identity by name. Legacy rows without references use exact-name
lookup only when no reviewed or known external collision exists. The old built-in and stored
alias path remains available when the asset cannot be used.

The app opens this asset only when Today needs matching. One serial reader closes the SQLite
connection after resolving current fixtures. There is no permanent participant cache. Runtime
lookups use 256-row pages, and the aggregate dictionary input has a 2 MiB / 16,384-phrase limit.
An overflow discards that scan's dictionary contribution rather than truncating a group.
The file limit is 16 MiB; the full synthetic schema measures 13,475,840 bytes. Its complete
40,371-identity dataset is packaged only in the instrumentation APK. The production slice is
81,920 bytes. Disk size and the bounded in-memory index are separate measures.

An explicit fixture competition takes priority over membership hints. Legacy fixtures without
a resolved competition may use the intersection of the opponents' dated memberships. Cup and
league memberships can coexist. Open-ended memberships provide no fallback context; a dated
season is not treated as a permanent domestic league. These hints affect only ranking after
existing confidence, language, score and time priorities.

Competition tie-break code has separate synthetic tests. This initial slice asserts no league
memberships or competition mappings; add those from verified evidence rather than inferring a
country's roster. Hardware acceptance and the frozen preview are tracked in the integration plan.
