# Library organisation

> Current behaviour of Sohva TV 0.1.0-beta.23 (build 57). Rebuild target: same behaviour,
> same look, cleaner implementation.

Layouts: the Library manager is extracted in
[design/03-screen-layouts.md](../design/03-screen-layouts.md) §5.3, the Settings rows in
[design/screens/settings.md](../design/screens/settings.md) §6 "Library (METADATA)". Consumers:
[Live TV guide](20-live-tv-guide.md) (GUIDE-FR-nn), [Channel management](21-channel-management.md)
(CHAN-FR-nn), [Movies and series](40-movies-and-series.md) (VOD-FR-nn), [Profiles](04-profiles-parental.md)
(PROF-FR-nn); work keys and the identity pass are [Metadata](41-metadata-enrichment.md)
(META-FR-nn); the backup flow is [Backup and restore](71-backup-restore.md).

## 1. Summary

The library has three **rooms** — Live TV, Movies and Series — each made of the provider's
**groups** (playlist group titles, Xtream categories) and their **items** (channels, films,
series). Library organisation lets the household decide what is shown and in which order: hide a
whole group, hide one item inside one group or everywhere, sort groups and each group's content
(provider order, A–Z, Z–A, newest, oldest, rating, or by hand), place the History, Favourites and
Recently-watched shortcuts, and keep custom channel lists in the same scheme. Rules are stored
once, device-wide, and every surface applies them: the guide, the player's channel lists, the film
and series walls and counts, Home, Search and Sohva Sport's channel matching. A restricted profile
narrows further on top. The two-pane **Library manager** is where rules are made, from the guide,
the walls and Settings. **Groups of your own** (genre groups for films and series) are defined in
Settings › Library. The rebuild must evaluate rules without a stack of correlated sub-queries per
row: joined in the wrong order they made Home's Continue watching take 5.5 s.

## 2. Feature checklist

Rules
- ORG-01 Three rooms (Live TV, Movies, Series) with independent rules.
- ORG-02 Show or hide a provider group, for one source or for the same-named group across all sources.
- ORG-03 Show or hide an item inside one group; its other groups are unaffected.
- ORG-04 "Hide everywhere" / "Show everywhere" for an item.
- ORG-05 Hiding a group keeps its members' own choices; showing it again restores them.
- ORG-06 Group order: original/provider, A–Z, Z–A, manual.
- ORG-07 Room default content order — Live: provider, A–Z, Z–A, manual; Movies and Series: A–Z, Z–A, newest release, oldest release, highest rating, manual. Missing year or rating sorts last.
- ORG-08 Per-group content order overriding the room default; "Use library default"; "Use default in every group…".
- ORG-09 Manual order of groups and items: Move mode, Move to top, Move to bottom, Move to position.
- ORG-10 The first manual order starts from the order on screen; switching to an automatic order and back restores the saved manual order.
- ORG-11 Shortcuts as groups: Favourites and Recently watched (Live), History (Movies, Series) can be hidden and placed.
- ORG-12 Custom channel lists appear as groups (Live): hide the list's guide entry, hide or order members inside the list only.
- ORG-13 A film carried by several playlists is one item (film identity); a choice made on it follows every copy.
- ORG-14 Items of a disabled source are shown as unavailable and no rule can enable them.
- ORG-15 Old "hidden categories" settings migrate into rules once.
- ORG-16 Every surface applies the same rules (guide, player lists and zapping, walls, rail counts, History, Home, Search, sport channel matching).
- ORG-17 A restricted profile sees less than the rules allow, never more.

Library manager
- ORG-18 Two panes: groups on the left, the selected group's content on the right.
- ORG-19 Room buttons (Live TV, Movies, Series), source scope ("All sources" or one source), filter All / Enabled / Disabled.
- ORG-20 "Search this pane" and Clear.
- ORG-21 Each group shows enabled / total; shortcuts show "Automatic view".
- ORG-22 Group menu: Disable/Enable group, Content order, Move, Move to top, Move to bottom, Move to position, Reset this group.
- ORG-23 Item menu: Hide/Show everywhere, Move, Move to top, Move to bottom, Move to position, with a note when the source or the group is disabled.
- ORG-24 OK on an item switches it on or off in this group.
- ORG-25 Select multiple, Select all matching entries, Enable/Disable selected or all matching, with a confirmation naming the count and scope.
- ORG-26 Undo of the last change.
- ORG-27 Loading, save-error and load-error states with Retry; a key-help line.
- ORG-28 The last group and source are remembered per room.
- ORG-29 "Advanced" (Live) opens Channel management.
- ORG-30 Opened from the guide's options, the walls' Options › Edit and Settings › Library.
- ORG-31 The All/Enabled choice is remembered and shared with Channel management's "Show hidden".

Groups of your own
- ORG-32 Up to 24 genre groups for Movies and Series: name, genres, year range, minimum rating.
- ORG-33 Settings list with a one-line summary per group, "None yet" when empty, "Add a group".
- ORG-34 Editor dialog: name, a switch per genre present in the library, from year, to year, rating at least; Save only when the group says something; Delete; Close.

Backup
- ORG-35 Rules and the film identities they depend on travel in encrypted backups; groups of your own and the show-hidden choice travel with the preferences.

## 3. Entry points and navigation

| From | Opens | Initial room, group, source | Back returns to |
|---|---|---|---|
| Guide options: "Sort: Playlist" or "Edit" (groups) (GUIDE-FR-94) | `LibraryManager(LIVE, group, source)` | Live, the guide's current group name and source | the guide with the options sheet open on the managed group (GUIDE-FR-95) |
| Movies / Series wall: Options › Edit (VOD-FR-47) | `LibraryManager(MOVIES or SERIES, group)` | the selected provider group, `@history` when History is selected, none for genre destinations | the wall, focus on Options |
| Settings › Library: "Manage groups & content" (`manager_title`, help `manager_row_help` "Hide, order and rename groups and channels.", Settings icon, empty value) | `LibraryManager(LIVE)` | Live, no group | Settings, the pane's last focused row |
| Manager "Advanced" (Live only) | `ChannelEditor` ([Channel management](21-channel-management.md)) | – | the manager |

- ORG-FR-01 Start location: when the requested room is the initial room and a group was given, use
  that group and source; otherwise the room's remembered location (§6), else none. A group given
  as a name is turned into its name key (`name:<lower-cased trimmed name>`); values starting with
  `@` or `name:` are kept. Nothing is drawn until the location has been read.
- ORG-FR-02 Focus on entry: the start group if it is visible, else the first visible group
  (scrolled into view). With a load error: the Back button.
- ORG-FR-03 Back, innermost first: close the confirmation → close the menu → cancel Move mode →
  leave selection mode (clearing the selection) → clear a non-empty search → leave the manager.
  After closing a dialog or cancelling a move, focus returns to the focused item (items pane), else
  the selected group, else Back.
- ORG-FR-04 Settings › Library "Groups of your own" rows open the editor dialog in place (4.10).

## 4. Behaviour

### 4.1 Keys and identities

- ORG-FR-05 **Group key** of a row: `id:<provider group id>` when the provider gave one (Xtream
  categories for films and series, Xtream live groups), else `name:<name trimmed and lower-cased,
  locale-independent>`; a missing name gives `name:`. **Name key**: always the name form. A channel
  with a custom group title (CHAN-FR-04) takes `name:<custom title>` as both. Rules are matched
  against both keys, so a rule written for a name also reaches an id-keyed group of that name.
- ORG-FR-06 **Item id**: channel id (Live), `vod:movie:<sourceId>:<movieId>` (films),
  `series:<sourceId>:<seriesId>` (series). **Identity**: for films the film identity from the alias
  table (4.6), else the item id; for channels and series the item id.
- ORG-FR-07 A **group as the manager shows it** is all items whose group name has the same name key
  (combined across sources when the scope is All sources), labelled with the first member's group
  name, or `manager_ungrouped` "Ungrouped" when blank. Its **backing keys** are
  `(room, scope source or "", name key)` plus every member's `(room, its source, its group key)`.

### 4.2 Rules

- ORG-FR-08 A rule is `(room, sourceId, groupKey, itemKey)` → `enabled` (true / false / none),
  `sort` (none or one of PROVIDER, TITLE_ASC, TITLE_DESC, NEWEST, OLDEST, RATING, MANUAL),
  `position` (none or ≥ 0). An empty `sourceId` means "every source"; an empty `itemKey` means the
  group itself; an empty `groupKey` with an item means "everywhere". Each field is independent: a
  rule may only sort, only place, or only show/hide. Only what differs from the defaults is stored.
- ORG-FR-09 Special keys:

  | Key (room, sourceId, groupKey, itemKey) | Meaning | Default |
  |---|---|---|
  | `(R, "", "", "")` | room default content order (`sort`) | Live PROVIDER, Movies/Series TITLE_ASC |
  | `(R, "", "@groups", "")` | group order (`sort`) | PROVIDER |
  | `(R, "", "@history" / "@favourites" / "@recent", "")` | shortcut shown (`enabled`) and place (`position`) | shown |
  | `(LIVE, "", "@list:<listId>", "")` | a custom channel list's guide entry shown / place | shown |
  | `(LIVE, "", "@list:<listId>", <channelId>)` | member shown / place **inside that list only** | shown |
  | `(LIVE, "", "@legacy-v1", "")`, enabled | marker: legacy migration done (4.7) | – |
  | `(R, S or "", <group key>, "")` | group shown / group sort / group place | – |
  | `(R, S or "", "", <identity or id>)` | item everywhere (Hide everywhere) | – |
  | `(R, S or "", <group key>, <identity or id>)` | item inside that group: shown / place | – |

- ORG-FR-10 Writes are **partial**: a change names the fields it sets (possibly to "none") and
  leaves the others alone; one action is one transaction; at most 100,000 changes per action.
  Rules are never deleted by the manager (a reset sets fields to none); rules whose groups or
  items disappear stay dormant and apply again when they come back.
- ORG-FR-11 Validation before any write (also for restores): at most 300,000 rules and 500,000
  aliases; a valid room; a known sort name or none; position ≥ 0; each key ≤ 2,048 characters; no
  duplicate rule keys; aliases non-blank, ≤ 2,048 characters, unique. A violation rejects the whole
  change.

### 4.3 Resolving what applies to an item (normative)

"First fields" over an ordered key list = for each field separately, the value of the first rule
in the list that sets it.

- ORG-FR-12 **Group rule** of an item = first fields over `(R, item source, group key)`,
  `(R, item source, name key)`, `(R, "", group key)`, `(R, "", name key)` (duplicates removed).
- ORG-FR-13 An item is **named** when some rule of its room has an `itemKey` equal to its identity
  or its id (group rules count as naming the empty id). An unnamed item has no item rules; this
  shortcut must give the same answers as the full lookup.
- ORG-FR-14 **Member rule** of an item in view `V` (V = its own groups, or a custom list key) =
  first fields over, in this order: for source in (item source, ""), for group in (V) or
  (group key, name key), for id in (identity, id) → `(R, source, group, id)`.
- ORG-FR-15 **Shown everywhere** = first `enabled` over `(R, source, "", identity)`,
  `(R, source, "", id)`, `(R, "", "", identity)`, `(R, "", "", id)`; if none, **not legacy-hidden**
  (Live: the channel's own hidden flag, CHAN-FR-27; others: false).
- ORG-FR-16 **Eligible** = source enabled AND shown everywhere AND group rule `enabled` is not
  false AND member rule (own groups) `enabled` is not false. **Enabled in view V** = eligible AND
  (V is the own groups, or the member rule for V is not false). So a hidden group hides its items
  whatever their own rules say; an item hidden everywhere stays hidden in every group and list; a
  member rule never enables an item of a disabled source.
- ORG-FR-17 Items are also restricted to the **active snapshot** of their source (the consumers'
  queries join it); not an organisation rule.

### 4.4 Ordering (normative)

- ORG-FR-18 Title comparison everywhere below: a Finnish collator at primary strength (case and
  accents ignored, Finnish alphabet order).
- ORG-FR-19 **Group order** of a room: mode = `@groups` sort (default PROVIDER). TITLE_ASC /
  TITLE_DESC compare group names; MANUAL compares each group's position (the smallest position among
  its members' group rules; none sorts last); every other mode keeps the input order (the caller's
  provider order: Live — the order the groups first appear in the playlist order; VOD — the
  query's case-insensitive name order, VOD-FR-03). Sorting is stable.
- ORG-FR-20 **Item sort** of an item = (own groups: its group rule's sort; list view V: the sort of
  `(R, "", V, "")`), else the room default rule's sort, else MANUAL when it is a Live channel with a
  legacy position (Channel management's order, CHAN-FR-29), else the room's built-in default.
- ORG-FR-21 **Ordered items** of a list (optionally a view V; History, Recently watched and search
  results pass `chronological` and keep their input order after filtering):
  1. keep items enabled in the view (management passes everything);
  2. with a view V: sort all with the comparator of the first item's item sort;
  3. otherwise group the items by group name, order the groups by ORG-FR-19, and inside each group
     split the members by item sort (in first-seen order of modes; a Live group whose members have
     legacy positions and no group or room sort rule is MANUAL as a whole) and sort each part with
     its comparator (sample = the first member with a legacy position, else the first member).
- ORG-FR-22 Comparators:
  - PROVIDER: provider order (Live: playlist position; VOD has none, so ties decide);
  - TITLE_ASC / TITLE_DESC: collator on the shown title;
  - NEWEST / OLDEST: year descending / ascending, items without a year last in both;
  - RATING: the leading number of the provider's rating string (`^\d+(?:[.,]\d+)?`, comma as the
    decimal point) descending, items without one last;
  - MANUAL: rank = member-rule position, else legacy position, else last;
  - ties: PROVIDER and MANUAL → provider order, then title, then identity; the others → title,
    then identity.
- ORG-FR-23 Positions are 0-based ranks written by the manager (4.9). New items of a manually
  ordered group have no rank and appear after the ranked ones, in provider order then title.
- ORG-FR-24 Changing a sort never deletes positions, so MANUAL → A–Z → MANUAL restores the manual
  order.

### 4.5 Where the rules apply

| Surface | What the rules do there | Spec |
|---|---|---|
| Guide group rail | groups with group rule false are left out; custom-list entries and Favourites/Recent filters hidden by their shortcut rules; with manual group sort, rail entries (favourites, recent, lists, groups) follow their positions | GUIDE-FR-30…35 |
| Guide channel lists, player channel list, channel up/down, dialled numbers | only eligible channels, ordered per ORG-FR-21 (GUIDE-FR-32) | 20, 30 |
| Custom channel list in the guide | the list's members that are eligible and enabled in view `@list:<id>`, in the list's order | CHAN-FR-55 |
| Movies / Series walls, group rail, counts | eligible titles only; group rail ordered by ORG-FR-19; History shown and placed by `@history` | VOD-FR-03…19 |
| Home Continue watching, Trakt rows | titles reached through the visible views by primary key | HOME-FR-10 |
| Search | eligible channels, films, series | 03 |
| Sohva Sport channel candidates | eligible channels | SPORT-FR-103 |
| Playback already running | continues when its item becomes hidden; later lists skip it | – |

- ORG-FR-25 Beta 23 applies rules two ways, and the two must agree:
  - **Kotlin** (`LibraryOrganization`): ordering everywhere and eligibility for in-memory lists;
  - **SQL views** `organization_visible_channels`, `organization_visible_movies`,
    `organization_visible_series`: the ORG-FR-15/12/14 predicate as three `COALESCE` chains of up to
    16 correlated `SELECT r.enabled FROM organization_rules r WHERE …` sub-queries per row (the
    film view gets the identity through a `LEFT JOIN organization_aliases`); for channels the group
    keys come from `COALESCE(preference.customOrganizationGroupKey, channel key)` and the legacy
    flag from `channel_preferences.hidden`. The same predicate is inlined
    (`ORGANIZATION_VISIBLE_LIVE_PREDICATE`) where a query must narrow channels first, and a shorter
    equivalent (`…_SOURCE_PREDICATE`) skips the item lookups when no rule naming a channel and
    deciding shown/hidden exists (a whole-source read: 360 → 130 ms on a desk for 50,000
    channels). Two more views (`organization_memberships`, `organization_eligible_items`) are
    defined but unused. The rebuild replaces all of this (§9.1).
- ORG-FR-26 Queries over the views pin their join order with `CROSS JOIN` from the small side
  (progress rows, source state) so a view is only reached by full primary key; `ANALYZE` runs after
  every playlist, guide and catalogue import activates (a fresh install has no statistics until
  then).

### 4.6 Film identities (aliases)

- ORG-FR-27 Table `organization_aliases(alias PK, identity)`, indexed by identity. Aliases are the
  films' content keys and `work:<work key>` (META-FR-66); a film's identity is the identity of its
  content-key alias. Aliases are detached from import snapshots, so a refresh never erases an
  identity.
- ORG-FR-28 **Registering a copy group** (a work alias plus the content keys that share it): read
  the known identities of the group's aliases; identity = the smallest known identity, else
  `film:` + the smallest alias of the group (for example `film:vod:movie:<source>:<id>`); every
  other identity found is **merged away**: all its aliases move to the chosen identity. Every
  alias of the group then points at the chosen identity. Identities only ever merge; nothing splits
  them (two films once folded by name stay folded even if TMDB later gives them different ids).
- ORG-FR-29 **Rules follow a merge**: every Movies rule whose `itemKey` is a merged-away identity,
  or an alias of the group other than the chosen identity, is re-keyed to the chosen identity. If a
  rule already exists at the target key the two combine: `enabled` = false if either is false,
  else the existing value, else the moved one; `sort` = the existing, else the moved; `position` =
  the smaller. Done in the same transaction.
- ORG-FR-30 When groups are registered: at import, per written batch of films (Xtream) or per
  activated snapshot (M3U), with work keys from name and year; by the identity pass after metadata
  matches (with `tmdb:` work keys) — [Metadata](41-metadata-enrichment.md) META-FR-69/70. Only the
  touched aliases and every alias of a merged-away identity are read (in chunks of 900).
- ORG-FR-31 Identity lookups for a list read only that list's ids, chunked (900); for more than
  5,000 ids beta 23 reads the whole alias table instead (**rebuild:** never; the identity is a
  stored column, §9.1). The Movies room re-evaluates on every alias write (observed through the
  alias count).
- ORG-FR-32 Management acts on identities: a film appears once per group however many copies the
  group holds, its counts count identities, and toggling it writes one member rule per copy's
  `(source, group key, identity)` in that group.

### 4.7 Migration of old settings

- ORG-FR-33 Old preference sets `hidden_live_categories`, `hidden_movie_categories`,
  `hidden_series_categories` (group names) become, once, rules `(room, "", name:<name>, "")` with
  `enabled = false` — only where no rule exists for that key — plus the marker rule
  `(LIVE, "", "@legacy-v1", "")` enabled. The marker makes it idempotent. It runs during start-up
  initialisation and after every backup restore. The old preferences are left intact (old backups
  stay portable).
- ORG-FR-34 Channel preferences keep working as inputs: the hidden flag (legacy hidden,
  ORG-FR-15), the channel order (legacy position, ORG-FR-20) and the custom group title
  (ORG-FR-05).

### 4.8 Profile restrictions

- ORG-FR-35 A profile restriction ([Profiles](04-profiles-parental.md) PROF-FR-20…24) is one set of
  **group keys** per room (empty = everything). It is applied **after** the rules: an item is shown
  to the profile only if it is eligible and its group key is in the set (when the set is not empty).
  Rules are device-wide; restrictions are per profile; a restriction never shows a hidden item.
- ORG-FR-36 The keys are the rows' group keys of ORG-FR-05 — `id:` keys for Xtream categories, name
  keys otherwise (PROF-FR-20's "normalised names" is exact only for M3U sources). The choices
  offered in Settings (Live: the guide rail's groups; films and series: every group of the active
  catalogues) ignore the rules, so a hidden group can still be allowed — and stays hidden.
- ORG-FR-37 Beta 23 applies restrictions in Kotlin after ordering (walls, group rails, guide rail
  and timelines, search); gaps are listed in PROF-FR-23 and VOD-FR-107. **Rebuild:** inside every
  query (§9.1).
- ORG-FR-38 From a restricted profile, Settings (and so its "Manage groups & content" row) asks
  for the parental PIN when one is set (PROF §4.5); the guide and wall entry points to the
  manager are **not** gated in beta 23 (open question 6).

### 4.9 The Library manager

**State.** Room; source scope (none = All sources); selected group; pane (GROUPS or ITEMS);
focused item; search text; filter; selection mode and selected keys; open menu; pending move;
pending confirmation; undo set; saving and error flags.

- ORG-FR-39 **Reading.** Beta 23 reads the whole room: every channel of every source (including
  hidden and disabled-source ones) with its source name, legacy flags and logo; or every active
  film/series of every source with poster, year, rating and source-enabled flag; plus identities,
  rules, custom lists and memberships (Live). Groups and their summaries (ORG-FR-43) are built off
  the main thread whenever the library, the rules or the scope change. A read error shows
  `manager_load_error` "Could not load the library. Your saved organization is unchanged." with
  "Retry" (`manager_retry`), and focus goes to Back. While loading: `manager_loading` "Loading
  library…". **Rebuild:** §9.2.
- ORG-FR-40 **Group list** (left pane) = shortcuts (Live: Favourites `manager_favourites`,
  Recently watched `manager_recent`; Movies/Series: History `manager_history`), then (Live) the
  custom channel lists (key `@list:<id>`, name = list name, members = the list's channels in the
  scope with the list's order as their legacy position), then the provider groups (ORG-FR-07) in
  group order (ORG-FR-19). With MANUAL group sort the whole list is ordered by each entry's
  smallest backing-key position (none last).
- ORG-FR-41 **Scope** button: label = the source's name or `manager_all_sources` "All sources";
  OK cycles All → each source (ordered by source id) → All; clears the selection and re-runs the
  initial focus. Group actions in a source scope write only that source's keys; in All sources they
  write the combined key and every member source's key (ORG-FR-07).
- ORG-FR-42 **Filter** button: `manager_all` "All" → `manager_enabled` "Enabled" →
  `manager_disabled` "Disabled" → All. Choosing All stores "show hidden" = on, Enabled stores off
  (shared with Channel management, CHAN "Show hidden"); Disabled is a passing look and is not
  stored. The manager opens on All when "show hidden" is on (default), else Enabled. The filter
  applies to both panes (a group's state for groups, an item's enabled-in-view for items).
- ORG-FR-43 **Group row**: mark "✓" (shown) or "—" (hidden) or "●" (selected in selection mode);
  title = label; subtitle = `manager_automatic_view` "Automatic view" for shortcuts, else
  "<enabled> / <total>" counting identities. A group is shown when (shortcut or list) its shortcut
  rule is not false, else when any member's group rule is not false. Enabled count = identities
  enabled in the group's view.
- ORG-FR-44 **Items pane**: header = the group label (Bold, 1 line) and, except for shortcuts, a
  compact "Content order" button (`manager_item_sort`); items = the group's members ordered per
  ORG-FR-21 with management's "include everything", one row per identity: mark, 40 dp image (logo
  or poster), title, subtitle = source name. Shortcuts have no items and show
  `manager_automatic_help` "This shortcut uses eligible content automatically. Its history is
  preserved when hidden."; an empty filtered list shows `manager_empty` "No matching entries. Clear
  search or change the filter. Disabled entries can always be restored here." (padding 16,
  `textMuted`).
- ORG-FR-45 **Following the rail**: focusing a group selects it; the items pane switches to it
  only after focus has rested **150 ms** (passing through groups reads nothing). The selected group
  and scope are stored for the room **1 s** after the rail settles (`manager_group_<ROOM>`,
  `manager_source_<ROOM>`).
- ORG-FR-46 **Search** (`manager_search` "Search this pane", edit on OK, normal size) filters the
  focused pane: group names (groups pane) or item titles (items pane), case-insensitive substring.
  "Clear" (`manager_clear`) empties it. Entering the items pane, leaving it, and beginning a move
  clear it.
- ORG-FR-47 **Keys on a group row**: Right → the items pane (first item focused, scrolled to the
  top); if the group's items are still being read, as soon as they arrive; if it has no visible
  items, the group menu opens. OK → the group menu (in selection mode: toggle the group's
  selection).
- ORG-FR-48 **Keys on an item row**: Left → back to the selected group (clears search and
  selection); Right → the item menu; OK → in selection mode toggle its selection; else, if the item
  is off because its source is disabled, it is hidden everywhere or its group is hidden, open the
  item menu; otherwise switch it on/off **in this group** (custom list: in the list's view).
- ORG-FR-49 **Group menu** (dialog titled with the group label): `manager_disable_group` "Disable
  group" / `manager_enable_group` "Enable group" (writes `enabled` on every backing key; shortcuts
  and lists: their shortcut key); "Content order" (not for shortcuts; opens the content-order
  menu); `manager_move` "Move"; `manager_top` "Move to top"; `manager_bottom` "Move to bottom";
  `manager_position` "Move to position"; `manager_reset` "Reset this group" (a confirmation that
  sets enabled, sort and position to none on **every** rule of this room whose source and group
  match a backing key — group and member rules alike); `action_back` "Back".
- ORG-FR-50 **Item menu** (title = item title, 2 lines): a note `manager_source_disabled` "This
  source is disabled. Enable it in Settings to make its content available." when its source is
  disabled, else `manager_parent_disabled` "This provider group is disabled. Enable the group to
  browse its content." when its group is hidden; `manager_hide_everywhere` "Hide everywhere" /
  `manager_show_everywhere` "Show everywhere" (writes `(R, "", "", identity)`); Move; Move to top;
  Move to bottom; Move to position; Back.
- ORG-FR-51 **Content-order menus** (title-less list of choices):
  - "Group order" (`manager_group_sort`, top bar): Original / provider order, A–Z, Z–A, Manual
    order. Choosing Manual when no group has a position writes positions for every group in the
    current order (seeding) plus `@groups` = MANUAL; otherwise it writes only `@groups`.
  - "Default order" (`manager_default_sort`, top bar) and "Content order" (per group): Live —
    Original / provider order, A–Z, Z–A, Manual order; Movies/Series — A–Z, Z–A, Newest release
    first, Oldest release first, Highest rating first, Manual order. Labels `manager_provider`,
    `manager_az`, `manager_za`, `manager_newest`, `manager_oldest`, `manager_rating`,
    `manager_manual`.
  - Default order = Manual seeds positions for every group except the shortcuts (custom lists
    included) that inherits its sort and has no positions yet (member keys only, in the group's
    current order), then writes the room default. Other choices write the
    room default only. Extra entry `manager_reset_sorts` "Use default in every group…" → a
    confirmation clearing `sort` on every group rule of the room (not `@groups`).
  - Content order = Manual on a group without positions seeds its members' positions in the
    current order and sets the group's sort to MANUAL; other choices set the sort on the group's
    backing keys. Extra entry `manager_inherit` "Use library default" clears the group's sort.
- ORG-FR-52 **Move mode** (groups or items): begins with the whole list of the pane (all groups;
  all of the group's items, not only the filtered ones), clears search and selection, keeps the
  filter, and focuses the moved row. Up/Down moves it past the **visible** neighbour (a filtered-out
  row is never the swap partner); OK places it: the new order is written as positions 0…n−1 on
  every entry's keys and the pane's sort set to MANUAL (groups: `@groups`; items: the group's
  backing keys); Back cancels without writing. "Move to top" / "Move to bottom" / "Move to position"
  enter Move mode with the row already at that place (position = 1-based number typed in a digits
  field, max 6 digits; empty does nothing) — OK still confirms. The key-up of the placing OK and of
  a cancelling Back is swallowed so it does not reach the screen below. The footer shows
  `manager_move_help` "Move: Up/Down · OK: place · Back: cancel".
- ORG-FR-53 **Bulk** (`manager_bulk` "Select / bulk", top bar) menu: `manager_select_multiple`
  "Select multiple" / `manager_selection_done` "Finish selection" (toggles selection mode, empties
  the selection); `manager_select_all` "Select all matching entries" (selection mode on, every
  visible row of the focused pane selected); `manager_enable_selected` "Enable selected / all
  matching…" and `manager_disable_selected` "Disable selected / all matching…" — the selected rows,
  or every visible row when not in selection mode — open a confirmation. Footer in selection mode:
  `manager_selection_help` "OK: select · Select / bulk: apply to selection · Back: exit selection".
- ORG-FR-54 **Confirmation** (bulk, reset, reset sorts): `manager_confirm` "Apply changes to %1$d
  entries?" where the count is the number of distinct item keys (or group keys for group-level
  changes) in the change set; a line "<scope> · <room>"; `manager_apply` "Apply" (writes, clears
  the selection) and Back (writes nothing).
- ORG-FR-55 **Saving**: one save at a time (another action while saving is ignored); footer
  `manager_saving` "Saving…"; on failure the database is unchanged, the footer shows
  `manager_save_error` "Could not save. Your previous settings are unchanged; please try again."
  and focus stays on the row. Rows stay in place after a toggle (they are dimmed, not removed,
  unless the filter excludes them; then focus moves to the row now at the same index, else the
  group, else Back).
- ORG-FR-56 **Undo** (`manager_undo`, footer, shown after a successful save while not saving):
  writes back, for exactly the fields the last action changed, the values those keys had before;
  one level; undoing is not itself undoable.
- ORG-FR-57 Footer help otherwise: `manager_help` "Groups: OK for actions, Right for content ·
  Content: OK enable/disable, Right for actions". Priority: load error, loading, save error,
  saving, move, selection, help.
- ORG-FR-58 Top bar (left to right): Live TV / Movies / Series (`manager_live`, `manager_movies`,
  `manager_series`; `selected` = current room; ignored while saving or moving; switching room
  opens it at its remembered group and scope with pane, search, filter, selection, menus and undo
  reset); scope; filter. Second row: search field, Clear, Group order, Default order, Select / bulk, and (Live) "Advanced"
  (`manager_advanced`).

### 4.10 Groups of your own

- ORG-FR-59 Model (VOD-FR-11): `id` (stable across renames), `name`, `genres` (set of the 22
  genres), `fromYear`, `toYear` (inclusive), `minRating` (out of 10). **Usable** only with a
  non-blank name and at least one condition. Matching and wall behaviour: VOD-FR-11 and VOD-FR-04.
- ORG-FR-60 Storage: DataStore key `custom_catalogue_groups`, a JSON array of
  `{"id", "name", "genres": [wire values], "fromYear"?, "toYear"?, "minRating"?}`. On read, entries
  without id or name, with unknown genres (dropped from the set) that leave the group unusable, or
  unparsable are dropped; an unparsable array reads as empty (never an exception). Save: name
  trimmed and cut to 40 characters; an unusable group is refused silently; a group with the same id
  is replaced, otherwise the new one is appended; the list is cut to **24**. Delete removes by id.
  Device-wide (shared by all profiles); in backups (`customCatalogueGroups`).
- ORG-FR-61 Settings › Library group "GROUPS OF YOUR OWN" (`custom_group_heading`) with the overline
  help `custom_group_help` "A name of your own over a handful of genres, shown above them in the
  library."; `custom_group_none` "None yet" (Info icon) when empty; one value row per group (title =
  name, value = summary, test tag `custom-group-<id>`) opening the editor; a row "Add a group"
  (`custom_group_add`) with a compact "Add a group" button (tag `custom-group-add`) opening an empty
  editor.
- ORG-FR-62 Summary: the genre labels (in vocabulary order) joined ", "; the years
  "from-to" / "from-" / "-to"; `custom_group_rating_at_least` "%1$.1f and up"; the present parts
  joined " · ".
- ORG-FR-63 Editor (dialog, 4.10 anatomy in §5.2): title `custom_group_title` "Group of your own";
  `custom_group_name` "Name" (first focus, text, cut to 40); overline `custom_group_genres` "Genres
  in this group" over one row per genre **present in the library** (distinct genres of the
  library's titles, vocabulary order; none before metadata has run) with a switch each; three fields
  `custom_group_from_year` "From year" and `custom_group_to_year` "To year" (digits only, at most
  4) and `custom_group_min_rating` "Rating at least" (at most 4 characters, a comma read as a
  decimal point; unreadable = none); buttons `action_save` "Save" (Check icon, enabled only when the
  group would be usable; saves and closes), `action_delete` "Delete" (danger, only for a saved
  group; deletes and closes), `match_picker_close` "Close". Back closes without saving.
- ORG-FR-64 A new group's id is `group-` + the name lower-cased with every run of characters other
  than `a–z`/`0–9` replaced by `-`. Consequences (bugs, rebuild fixes): two groups with the same
  name share an id and the second replaces the first; names without Latin letters collide
  ("group--"); the 25th group is dropped silently. **Rebuild:** random stable ids; "Add a group"
  disabled at 24 with the note "Up to 24 groups" (new string).
- ORG-FR-65 Groups of your own are not in the Library manager in beta 23 (open question 4).

### 4.11 Backup format of the organisation

- ORG-FR-66 In the encrypted backup payload (format 2, [Backup and restore](71-backup-restore.md))
  under `"organization"`:
  ```json
  {"rules":[{"room":"LIVE","sourceId":"","groupKey":"name:news","itemKey":"",
             "enabled":false,"sortMode":null,"position":null}],
   "aliases":[{"alias":"vod:movie:src1:123","identity":"film:vod:movie:src1:123"}]}
  ```
  Every field of a rule is required; `enabled`, `sortMode` and `position` may be `null`. Format 1
  backups carry none (an empty organisation is restored).
- ORG-FR-67 Export writes **all rules** but only the aliases that rules depend on: every alias of
  every identity named by a Movies item rule, including identities reached through a rule that
  names an alias directly, ordered by alias (the full alias table can hold hundreds of thousands of
  rows and exhausted a TV's heap when expanded to JSON).
- ORG-FR-68 Restore: validate (ORG-FR-11) before writing; then, in one transaction, delete all
  rules and aliases and insert the backup's; then run the legacy migration (ORG-FR-33). The
  identity pass rebuilds the rest of the alias table on the next catalogue activation or metadata
  change (**rebuild:** the restore also clears the identity pass's mark so it runs at once,
  META-FR-69).
- ORG-FR-69 Also in the backup's preferences: `customCatalogueGroups` (ids and names cut to 20,000
  characters, unusable groups dropped, first 24 kept), `editorsShowHidden`, `preferredCatalogueCopy`
  (VOD-FR-32), the three legacy hidden-category lists and the profiles' allowed group keys
  ([Profiles](04-profiles-parental.md)). Manager locations are not.

## 5. Screen anatomy

### 5.1 Library manager

[design/03-screen-layouts.md](../design/03-screen-layouts.md) §5.3 has the layout (header 25 sp
Bold + Back; room/scope/filter row `spacedBy 6`; search row; groups `LazyColumn` **290 dp**
`spacedBy 4`; content column weight 1; footer 12 sp `textMuted`, padding top 7; row **57 dp**, 8 dp
corners, `surface` / focused `surfaceFocused` with a **2 dp `accent` border**, padding h 10,
`spacedBy 9`, mark, 40 dp image, title 14 sp, subtitle **10 sp**; menus 460 × ≤ 480 dp,
`surface`, 16 dp corners, padding 18, scrolling `Column(spacedBy 5)` of full-width compact buttons,
focus on the dialog's content when it opens). Mark colours: "✓" `accent`, "—" `textMuted`; title
`textPrimary`, `textMuted` when off. Both lists are focus groups. The group label in the items
header is Bold, 1 line, ellipsis.

Rebuild look (owner decisions recorded in the design system): subtitle 12 sp (10 sp is below the
floor), dialogs on `panel`, no default click indication; whether the orange focus stays is open
question 1.

Test tags: `manager-back`, `manager-room-<LIVE|MOVIES|SERIES>`, `manager-source`, `manager-filter`,
`manager-search`, `manager-clear`, `manager-bulk`, `manager-groups`, `manager-group-<key>`,
`manager-items`, `manager-item-<identity>`, `manager-retry`, `manager-undo`.

### 5.2 Group-of-your-own editor

Platform dialog: panel `fillMaxWidth(0.66) × fillMaxHeight(0.88)`, clip large, `panel`, padding
24, `spacedBy 12`, test tag `custom-group-editor`. Title (title style 28/32, Bold, `textPrimary`);
name field (compact, full width); overline; genre `LazyColumn` (weight 1, `spacedBy 2`) of
`SettingsRow` (genre label; Check icon when chosen; trailing Settings switch); `Row(spacedBy 10)`
of the three compact number fields (weight 1 each); `Row(spacedBy 10)` of compact buttons Save,
Delete, Close. Tags `custom-group-name`, `custom-group-genres`, `custom-group-genre-<wire>`,
`custom-group-from-year`, `custom-group-to-year`, `custom-group-min-rating`, `custom-group-save`,
`custom-group-delete`, `custom-group-close`.

### 5.3 Settings rows

[design/screens/settings.md](../design/screens/settings.md) §6 "Library": "Manage groups & content"
is the third value row of group 2; "Groups of your own" is group 3.

## 6. Data

| Store | Key | Content | Per profile | Backup |
|---|---|---|---|---|
| `organization_rules` (Room) | (room, sourceId, groupKey, itemKey); index `itemKey` | enabled (nullable bool), sortMode (nullable text), position (nullable long) | no | yes (all) |
| `organization_aliases` (Room) | alias; index `identity` | identity | no | only the families rules depend on |
| `channel_preferences` | channelId | hidden, sortOrder, customGroupTitle, customOrganizationGroupKey (inputs, [Channel management](21-channel-management.md)) | no | yes (spec 21) |
| DataStore `custom_catalogue_groups` | – | JSON array (ORG-FR-60), ≤ 24 | no | yes |
| DataStore `editors_show_hidden` | – | bool, default true | no | yes |
| DataStore `manager_group_<ROOM>`, `manager_source_<ROOM>` | – | last group key and source id (≤ 2,048 chars each) | no | no |
| DataStore `hidden_live_categories`, `hidden_movie_categories`, `hidden_series_categories` | – | legacy name sets, read by the migration | no | yes |
| DataStore `allowed_groups_*[:profile]` | – | profile restrictions (spec 04) | yes | yes |

- Organisation rows (catalogue columns used): `organizationGroupKey` and `organizationNameKey` on
  channels, films and series (ORG-FR-05), set at import.
- Nothing is per profile except restrictions; the rules are the household's.
- **Rebuild data changes** (for [Data model](../plan/04-data-model.md)): the stored visibility and
  identity columns and the group-state table of §9.1; sparse positions (§9.3).

## 7. External interfaces

None over the network. The backup JSON (ORG-FR-66) is the only file format. The manager and the
editor open no web pages.

## 8. Edge cases and limits

- **Same group name in two sources**: one manager group in All sources (combined key plus each
  source's key); separate groups per source scope. A combined action writes every source's key; a
  source-scoped action never touches another source.
- **Provider renames a group**: Xtream groups keep their `id:` key (rules follow the id); M3U groups
  are name-keyed, so a renamed M3U group is a new group and the old rules sleep. No fuzzy merging.
- **A group disappears and comes back**: its rules and member ranks apply again (dormant rules).
- **Source disabled**: its items are ineligible everywhere; the manager still lists them (dimmed,
  item menu explains); re-enabling the source restores everything.
- **Every group hidden**: walls fall back to "all groups" (VOD-FR-09); the guide rail shows only
  the shortcuts; the manager still lists everything under All.
- **Hidden while playing**: playback continues; the next zap skips it.
- **Concurrent import and manager write**: writes are partial-field transactions, so an import
  adding items never loses a manual rank, and a move never removes new items (they append after
  the ranked ones).
- **Manual order with filters**: moves step over filtered-out rows but rank the whole list.
- **Large seeded groups**: seeding Manual on an 8,000-item group writes 8,000 member rules in beta
  23 (and every move rewrites them all); validation caps the table at 300,000 rules.
- **Custom list member hidden in the list**: stays visible in its provider group.
- **Legacy hidden channel shown by a rule**: an item rule naming the channel wins over the
  channel's hidden flag.
- **Two copies of one film in one group**: one manager row; toggling writes a member rule for each
  copy.
- **Film identity merges wrongly** (two different films with the same cleaned name and year):
  they stay one identity; a rule on one applies to both (no split exists; open question 5).
- **Restore to another device**: rules name source ids and group keys; they apply once the same
  sources are imported (sources are restored with the same ids).
- **Fresh install**: no planner statistics until the first import activates; queries must stay
  fast without them (§9.1).

## 9. Lightweight by design

### 9.1 Index-driven rule evaluation (normative for the rebuild)

Beta 23 answers "is this row visible?" with up to **16 correlated sub-queries per row** in SQL
views. It works only when the planner reaches each row by primary key; joined from the other
side it walked every title with all 16 lookups (Home's Continue watching: under 1 ms → 5.5 s on a
40,000-title library, [Lessons](../plan/08-lessons-learned.md) 2.1), and a whole-source channel read
spent two thirds of its time in them. An early correlated-UNION design took about 20 s
(GROUP_CONTENT_MANAGEMENT_IMPLEMENTATION). The rebuild decides visibility **once per row and
stores it**:

- **Resolver in memory.** Rules are few (typically hundreds to a few thousand). A pure Kotlin
  resolver holds them in hash maps keyed by the full key, a per-room set of named item keys, and a
  memo of group decisions per `(room, source, group key, name key)` (a source has a few hundred
  groups). It implements 4.3 and 4.4 exactly and is covered by table-driven tests.
- **`org_group_state`** table `(room, sourceId, groupKey)` PK → `shown`, `sort`, `position`: one row
  per group present in an active snapshot plus the shortcut and list rows (a few thousand at most),
  rewritten in one transaction whenever rules change or an import activates.
- **Stored item columns** on channel, film and series rows: `identity` (films; written at import
  from the work key and updated by the identity pass) and `orgVisible` (0/1) = shown everywhere AND
  group shown AND member rule not false (ORG-FR-15/12/14, legacy hidden flag and custom group key
  included; source enabled and active snapshot stay join conditions on the small source table).
  Page queries filter `orgVisible = 1` through an index whose leading columns match the page
  query's filter and order (spec 40 §9.3, spec 20).
- **Incremental maintenance** (on the `bulk` dispatcher, pages of 2,000 in key order, one
  transaction and one invalidation per page):
  - import: computed while each page is written (the resolver is in memory);
  - group rule change: only rows with that group key or name key (and that source when the rule
    names one), through indexes on `(sourceId, groupKey)` and `nameKey`;
  - item rule change: only rows whose identity or id equals the rule's item key (index on
    `identity`);
  - channel preference change (hidden, custom group): that channel;
  - identity merge: the re-keyed films.
  A group toggle on an 8,000-title group settles in about four pages; the manager shows its own new
  state at once.
- **Not stored**: custom-list view rules (`@list:`) and shortcut rules — read with the list or rail
  they belong to (bounded); profile restrictions — applied in each query as `groupKey IN` a
  temporary table of the profile's allowed keys (a few hundred at most).
- **Equivalence**: a randomised test compares the stored `orgVisible` with the resolver and with
  beta 23's SQL predicate (kept in test code as the reference) over rules of every shape — group,
  name, combined, member, everywhere, legacy hidden, custom group key, alias identity (mirroring
  `OrganizationViewsTest` and `LibraryOrganizationEquivalenceTest`).
- **Query-plan tests** (JVM, SQLite on the exported schema, run once with an empty `sqlite_stat1`
  and once after `ANALYZE` on a synthetic large library): for every query that applies rules —
  guide group page, guide all-channels page, player channel list, VOD pages (group, all groups,
  genre, Unsorted, custom group, History, search), counts, Home continue watching, Search, sport
  candidates, manager group list and items page — assert no `CORRELATED SCALAR SUBQUERY` on the
  rules table, no full `SCAN` of a channel or catalogue table except documented primary-key walks,
  no `USE TEMP B-TREE FOR ORDER BY` in paged queries, and the intended join order (pinned with
  `CROSS JOIN` where a statistic-free planner would guess wrong).
- **ANALYZE** after each import activation on the `bulk` dispatcher (and marked pending for the
  daily maintenance otherwise); never on the main thread or at start-up.

### 9.2 The manager reads only what it shows

Beta 23 loads the whole room into memory (every channel of every source; every active film of
every source — 200,000 rows for the owner's catalogue), looks up identities for all of them (above
5,000 ids by reading the **whole alias table**), and builds every group in Kotlin. Rebuild:

- Group list with counts from one grouped query per (room, scope) over the stored columns (total =
  distinct identities, enabled = those with `orgVisible` and the membership not off), plus the
  shortcut and list rows; a few hundred rows.
- Items of the selected group only, read after the 150 ms rest, narrow rows (id, identity, title,
  source, year, rating key, provider order, legacy fields, image URL, states); read whole up to
  2,000 rows, else through the keyset window pager of spec 40 §9.3 (pages of 120, ≤ 5 pages held)
  in the group's effective order.
- Summaries recomputed only when data or rules change, off the main thread; no database read and
  no rule evaluation per D-pad press. (Beta 23 fix `8a32408`: each row recounted its group with 16
  rule lookups per channel on every focus move — one to two seconds per group on a Google TV
  dongle.)
- The location write is debounced (1 s) — a preference write per focus move made the whole app
  recompose.
- Images in manager rows: 40 dp (80 px) decodes, RGB_565, through the shared loader.

### 9.3 Cheap writes

- Sparse positions: ranks are written with gaps (step 1,024), so a move writes **one** rule
  (midpoint) and a group is renumbered only when a gap is exhausted. Seeding Manual still writes
  one rule per member, once. Beta 23 rewrites every member's position on every move.
- `change()` reads the rules once per action (bounded), applies partial fields, validates, writes;
  one invalidation per action.
- Alias registration reads only touched aliases (chunks of 900) and rewrites rules only when a merge
  moved one.

### 9.4 Memory

| Held | Bound |
|---|---|
| Rules resolver | the rules (≤ 300,000 by validation; typically ≤ a few thousand) + group memo (≤ groups) |
| `org_group_state` in memory for rails | ≤ a few thousand rows |
| Manager | group list (≤ ~1,000 rows) + one group's items (≤ 2,000 rows, or 600 in the pager) |
| Ordering a guide list | the list being shown (spec 20 pages the roster) |

Beta 23's `LibraryOrganization` allocated **220 MB** to order 50,000 channels (keys rebuilt and
lists copied per item) until group decisions were memoised and unnamed items answered without
lookups: 9 MB for the same answer (commit `31ae7f5`). The rebuild keeps both techniques and never
allocates per comparison (collation keys precomputed; spec 40 §9.3 `titleKey`).

### 9.5 Threads and start-up

- Rules are read after the first frame; the legacy migration runs once in the background start-up
  initialisation (it is a no-op after the marker exists).
- Rule evaluation for lists runs on the `ui` dispatcher (a page at a time), maintenance on `bulk`
  at background priority, pausing while video plays ([Architecture](../plan/03-architecture.md)
  §4.7) except viewer-started work
  (a manager action is viewer-started and runs at once).

### 9.6 Where the current app was slow or ran out of memory

1. Correlated view sub-queries joined from the wrong side: 5.5 s Continue watching (fixed with
   `CROSS JOIN` pinning and plan tests; rebuild removes the sub-queries).
2. The organisation state read every alias (one per film copy) on every guide read: 10 s to open
   the guide from playback and an ANR (commit `8c3046d`, 5 Sept 2026).
3. Identity folding re-read every film on every import batch and every matched title (same commit).
4. 220 MB of garbage ordering 50,000 channels (commit `31ae7f5`).
5. The manager recounting groups on every focus move and writing a preference per move (commit
   `8a32408`).
6. The manager holding a whole room (§9.2) — still present in beta 23.
7. Every move rewriting every position (§9.3) — still present.
8. The M3U import registering aliases for a whole activated snapshot in one read
   (`registerImportedSnapshot`) — rebuild pages it.

## 10. Lessons from the current app

1. **One rule model below the UI** (GROUP_CONTENT_MANAGEMENT_PLAN "Visibility and ordering
   contract"): eligibility and order are decided once and shared by every surface; custom lists,
   genres, History and search can never resurrect a hidden item.
2. **Source-aware backing keys, no fuzzy merging**: keep Xtream category ids; show same-named groups
   combined but write per-source keys; never guess identities across renames.
3. **Rules survive refreshes**: detached from snapshots, partial-field writes, dormant rules kept;
   a refresh never enables a hidden group.
4. **Automatic sorts keep manual ranks** so Manual can be switched back.
5. **Film identities merge, never split** (`OrganizationDao.registerFilmAliases`); preferences
   follow a merge. A split would orphan choices; the owner has not asked for one (open question 5).
6. **Pin join order and run ANALYZE** ([Lessons](../plan/08-lessons-learned.md) 2.1): Room never runs
   ANALYZE; the planner guessed and walked the catalogue.
7. **Views must stay equal to the Kotlin rules**: beta 23 keeps a unit test holding the inlined and
   shortened SQL predicates to the view predicate, and an equivalence test holding the memoised
   `LibraryOrganization` to a reference implementation (`ReferenceLibraryOrganization`). Keep the
   same discipline for the stored flag.
8. **Manager focus**: moves step past the visible neighbour (a hidden group was a silent swap
   partner, commit `a6674f9`); dialogs own focus and Back; key-ups of placing OK and cancelling Back
   are swallowed; focus returns to the row that opened a dialog.
9. **Dead code**: views `organization_memberships` and `organization_eligible_items`, the
   `resetGroup` DAO method, `observeRecords`, the guide's and the wall's old in-place category edit
   modes (GUIDE-FR-97, VOD-FR-48) are unused in production; do not rebuild them.
10. **Backups carry only the aliases rules need** (commit `97c7264` onwards,
    `OrganizationDao.backupSnapshot`): the full table exhausted a TV's heap as JSON.
11. **Bugs to fix**: groups of your own collide by name-derived id and the 25th is dropped silently
    (ORG-FR-64); after a restore the identity pass may not run until the catalogue changes
    (ORG-FR-68); the manager's scope cycles sources by id rather than name; the manager's
    subtitles are 10 sp.

Open questions (for the owner)

1. **Manager focus look**: keep the orange (`accent`) 2 dp border and `surfaceFocused` fill, or use
   the standard row fill flip ([design/01-design-system.md](../design/01-design-system.md) §17)?
2. **Settings entry room**: "Manage groups & content" always opens Live TV. Open on the last room
   used instead?
3. **Reset scope**: "Reset this group" also clears the group's member rules (hidden items and
   manual ranks). Keep, or reset only the group's own rule?
4. **Groups of your own in the manager**: add them as rows of the Movies/Series rooms (show/hide,
   place), or keep them in Settings only?
5. **Splitting a wrongly merged film** (same cleaned name and year, different films): offer
   "These are different films" in the item menu, or accept the merge?
6. **PIN for the manager** from a restricted profile: the guide's and walls' entry points reach it
   without the parental PIN, so a restricted profile can un-hide device-wide groups. Gate every
   entry point behind the PIN when a profile is restricted?
7. **Dormant rules**: keep rules for groups and items that no longer exist forever (today), or
   sweep those unseen for, say, 90 days?
8. **Confirmation before a single "Disable group"** (bulk actions confirm; single toggles do not,
   and Undo exists)? Recommend no.

## 11. Acceptance tests

Unit (JVM)
- Resolver (mirror `LibraryOrganizationTest`): a disabled parent wins without erasing member
  choices; a local hide does not hide another membership; a source override does not affect
  another provider; provider id identity survives a rename and an explicit override beats a legacy
  alias; a global film hide follows the standing copy and cannot be bypassed through a genre;
  manual order returns after an automatic sort and new entries append; missing year and rating
  stay last in both directions; a room default does not replace an explicit group sort; chronology
  and management keep the original identity when hidden; legacy Live order keeps ranked before
  unranked until an explicit sort; a disabled source cannot be enabled by a member rule; custom
  list hide and order stay local; moving first/last/missing ids is safe.
- Equivalence: the memoised resolver equals a straightforward reference over random rule sets
  (mirror `LibraryOrganizationEquivalenceTest`); the stored `orgVisible` equals the resolver and
  beta 23's SQL predicate (§9.1).
- Manager model (mirror `LibraryManagerModelsTest`): a duplicate film is one row and a toggle
  targets every copy only in this group; source-scoped actions never target other sources; a
  manual move changes ranks and sort only, never visibility; disabled groups stay manageable and a
  restore keeps disabled members.
- Groups of your own (mirror `CatalogueCustomGroupTest`, `CustomGroupEncodingTest`): any listed
  genre matches; every set condition must hold; unset is not a condition; year range inclusive;
  unknown year fails a year bound; no genre never matches a genre group; rating is a floor;
  unusable groups refused; encoding round trip; malformed JSON reads as empty; unknown genres
  dropped; 24 limit; rebuild: unique ids for equal names.
- Backup codec (mirror `OrganizationBackupCodecTest`): round trip keeps hidden state, ranks and
  aliases; negative rank, unknown sort and duplicate keys are rejected before any write.
- Query plans (§9.1) for every listed query, with and without statistics.

Instrumentation (database)
- Mirror `OrganizationDaoTest`: migration keeps customisations and Finnish group keys;
  source-scoped visibility, counts and the playback lookup agree; an alias merge keeps a global
  hide and a member rank; partial updates keep unrelated fields and a snapshot refresh keeps
  overrides; first alias registration migrates rules made on raw copies; disabled parents apply to
  History, search, genres and new imports; customisation survives a database reopen and a source
  disable/enable; a concurrent import and manual move keep new items and overrides; alias
  registration reads only touched aliases and still merges across batches.
- Backup with custom groups and organisation restores both (mirror `BackupCustomGroupsTest`).

UI
- Mirror `LibraryManagerFocusTest`: toggling keeps focus and Left returns to the group; Back closes
  a menu without leaving; a failed save keeps the row focused and shows the message; bulk disable
  needs confirmation and Back writes nothing; Move cancels without writing and OK places; moving
  past a hidden group lands after the next one on screen.
- Genre rail with a group of your own above the genres; choosing it keeps only what it names; an
  empty group leaves focus somewhere useful (mirror `CatalogueCustomGroupRailTest`).
- Editor: Save disabled until usable; Delete only for saved groups; Back closes without saving.

Performance (low-end box or stand-in)
- Manager on a synthetic library of 56,000 channels in 800 groups and 200,000 films: opening each
  room shows the group list within 1 s, a D-pad move between groups renders within 1–2 vsyncs and
  reads nothing, Java heap stays within the plan/07 browse budget (mirror
  `LibraryManagerBenchmarkTest`: 40 groups × 60 vs × 2 items, move timings compared).
- Toggling an 8,000-title group updates the wall within 1 s of leaving the manager; the guide's
  and Home's queries keep their plan-test shapes on the large library.

## 12. Reference: current code map

- `core/.../model/LibraryOrganization.kt` — rooms, sorts, keys, rule resolution and ordering.
- `core/.../database/OrganizationDao.kt` — rules and aliases tables, partial changes, alias merges,
  backup snapshot, validation, identity-pass reads.
- `core/.../database/OrganizationViews.kt` — the visible views and inlined predicates.
- `core/.../database/CatalogueHomeQueries.kt`, `GuideChannelQueries.kt`, `GuideDao.kt`,
  `GuideSearchQueries.kt`, `SportsMatchQueries.kt`, `TraktHomeQueries.kt`,
  `TraktProgressQueries.kt`, `CatalogueDao.kt` — consumers of the views and predicates.
- `core/.../database/StreamMateDatabase.kt` — `analyze()`, view creation in migrations.
- `core/.../model/CatalogueCustomGroup.kt` — groups of your own and their matching.
- `core/.../app/AppPreferencesRepository.kt` — custom groups, show-hidden, manager location,
  identity mark, legacy hidden sets.
- `core/.../app/Profiles.kt` — `ProfileRestriction`.
- `iptv/.../repository/OrganizationRepository.kt` — state, restriction, identities per list, legacy
  migration, alias registration, identity pass, manager library, `organize`, category ordering.
- `iptv/.../feature/settings/LibraryManagerScreen.kt` — the manager screen and its menus.
- `iptv/.../feature/settings/LibraryManagerModels.kt` — groups, summaries and change builders.
- `iptv/.../feature/settings/SettingsCustomGroups.kt` — the group editor and summary.
- `iptv/.../feature/settings/SettingsScreen.kt` (section `METADATA`) — the Settings rows.
- `app/.../app/OrganizationBackupCodec.kt` — backup JSON of rules and aliases.
- `app/.../app/StreamMateBackupManager.kt` — where the codec and custom groups are written/read.
- Tests: `LibraryOrganizationTest`, `LibraryOrganizationEquivalenceTest`,
  `ReferenceLibraryOrganization`, `OrganizationViewsTest`, `OrganizationDaoTest`,
  `LibraryManagerModelsTest`, `LibraryManagerFocusTest`, `LibraryManagerBenchmarkTest`,
  `CatalogueCustomGroupTest`, `CustomGroupEncodingTest`, `CatalogueCustomGroupRailTest`,
  `OrganizationBackupCodecTest`, `BackupCustomGroupsTest`.
