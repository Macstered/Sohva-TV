# Sohva TV — rebuild kit

This folder is everything needed to rebuild Sohva TV from an empty repository: the plan, the
instructions for whoever writes the code, a specification of every feature, the visual design,
and every visual asset the current app ships.

The target, as the owner set it on 24 September 2026:

1. **Every feature** Sohva TV `0.1.0-beta.23` (Android build 57) has today.
2. **Almost identical look.**
3. **Lightweight**, so it runs well on low-end TV boxes (Amlogic S905Y4, 2 GB) as well as on an
   Nvidia Shield.
4. **No reuse of the old code**, except small pure pieces where reuse cannot affect performance.

The old code is reference material only: `G:\SportMate\.local\sohva-sport-user-reports\.local\beta19-public-source`
(public commit `efab52a`).

## How to use it

1. Create the new repository. Copy this kit into it (for example as `docs/rebuild/`), and put
   `AGENTS.md` and `CLAUDE.md` at the repository root.
2. Settle the owner decisions in [plan/09-owner-decisions.md](plan/09-owner-decisions.md): each has
   a default, so building can start before every answer is in.
3. Work through [plan/02-roadmap.md](plan/02-roadmap.md) milestone by milestone. Each milestone lists
   the specs it implements and its exit criteria, including performance budgets on the low-end
   stand-in.
4. Track parity in [plan/01-feature-inventory.md](plan/01-feature-inventory.md): every feature has
   an ID; tick it when its acceptance tests pass.

## Reading order

| # | File | What it gives you |
|---|---|---|
| 1 | [AGENTS.md](../../AGENTS.md) | The rules: lightweight, behaviour and look, security, devices, code reuse, how to work |
| 2 | [plan/00-product-overview.md](plan/00-product-overview.md) | Product, principles, identity that must not change, non-goals, glossary |
| 3 | [plan/07-performance.md](plan/07-performance.md) | Budgets per device class and the rules that meet them |
| 4 | [plan/03-architecture.md](plan/03-architecture.md), [plan/04-data-model.md](plan/04-data-model.md) | Target structure and data |
| 5 | [plan/02-roadmap.md](plan/02-roadmap.md) | Build order, milestones, exit criteria |
| 6 | `specs/` | One file per feature area, numbered requirements and acceptance tests |
| 7 | `design/` | Design system, components, screen layouts, icons and imagery, screenshots |
| 8 | [plan/08-lessons-learned.md](plan/08-lessons-learned.md) | What went wrong before, and the rule that prevents it |
| 9 | [plan/09-owner-decisions.md](plan/09-owner-decisions.md) | Open decisions with defaults, device checks, suspected bugs in beta 23 |

## Contents

```
README.md                    this file
AGENTS.md, CLAUDE.md         instructions for people and AI agents building the app
plan/
  _spec-conventions.md       how every spec is written; the lightweight budgets
  00-product-overview.md     product, principles, scope, identity, non-goals, glossary
  01-feature-inventory.md    every feature with its ID and spec
  02-roadmap.md              milestones M0–M11
  03-architecture.md         current architecture and the clean target
  04-data-model.md           database, preferences, files; migration options for existing users
  05-tech-stack-and-build.md libraries, versions, build types, R8, profiles, signing
  06-quality-testing-release.md  tests, CI, release process and the update-feed contract
  07-performance.md          budgets, rules, measurement
  08-lessons-learned.md      cross-cutting lessons
  09-owner-decisions.md      decisions with defaults, device checks, suspected beta 23 bugs
specs/                       feature specifications (01–74)
design/
  01-design-system.md        colour themes, type, spacing, shapes, focus, motion, cheap rendering
  02-components.md           shared components
  03-screen-layouts.md       index of every screen and overlay
  screens/                   code-derived layouts: guide, home and shell, movies and series,
                             settings, Sohva Sport
  04-icons-and-imagery.md    brand, icons, artwork rules, attribution
  screenshots/               reference screenshots (current and older builds, synthetic content)
assets/                      every shipped visual asset (drop-in res/ tree), SVG exports,
                             owner-supplied sources, preview.html contact sheet, inventory.json
reference/
  strings/                   every string resource in all seven languages
  tester-docs-beta23/        the public tester documents of beta 23
  owntv-study.md             ideas from another open-source TV app (ideas only)
```

## Provenance

Written on 24 September 2026 from the public beta 23 source tree, its tester documents, the
private development history and notes, and the old app's tests. Measurements were copied from the
code. Anything a spec could not determine is listed in that spec's open questions. Screens without
a current screenshot are specified from code; see
[design/screenshots/README.md](design/screenshots/README.md).
