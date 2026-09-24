# Sohva TV navigation icons

A coordinated set for all 14 navigation entries. The two Search entries use the same drawing, so there are 13 unique symbols.

## Contents

- svg/main-app/: eight individual SVGs.
- svg/inside-discover/: six individual SVGs.
- png/light/: near-white icons for dark backgrounds, at 24, 48 and 96 px.
- png/dark/: dark icons for light backgrounds, at 24, 48 and 96 px.
- sohva-icons.sprite.svg: all 13 unique symbols in one SVG sprite.
- manifest.json: labels, file paths and symbol IDs.
- preview.png: overview of the complete set.

## Design

24 × 24 viewBox; 1.75-unit strokes; rounded caps and joins; transparent backgrounds. SVGs use currentColor so inline icons inherit the navigation text color. The green in the preview is an example selected state, not a fixed asset color. Both Search icons are identical.

| Area | Navigation item | Symbol |
| --- | --- | --- |
| Main app | Front Page | Sofa |
| Main app | Live TV | Television with live indicator |
| Main app | Sohva Sport | Trophy |
| Main app | Movies | Clapperboard |
| Main app | Series | Stacked episodes |
| Main app | Search | Magnifier |
| Main app | Discover | Play with discovery sparkle |
| Main app | Settings | Gear |
| Inside Discover | Home | House |
| Inside Discover | Library | Books on a shelf |
| Inside Discover | Search | Magnifier |
| Inside Discover | Explore | Compass |
| Inside Discover | Addons & Setup | Puzzle piece |
| Inside Discover | Back to Home | House with return arrow |

## Use

Use the individual SVGs inline to inherit CSS color. Set their width and height to the desired icon size, for example 24 or 32 px. SVGs embedded with an img element do not inherit the parent element's currentColor; recolor the source, use a CSS mask, or use the supplied PNG variant in that case.

For the sprite:

```html
<button type="button">
  <svg width="24" height="24" aria-hidden="true">
    <use href="sohva-icons.sprite.svg#sohva-discover"></use>
  </svg>
  Discover
</button>
```

Keep the visible navigation label. When icons accompany visible labels, remove the SVG's role and aria-label and set aria-hidden="true" to avoid duplicate announcements. For an icon-only button, place its accessible name on the button.

The two home destinations are intentionally distinct: a sofa for the main Front Page, a house for Discover Home, and a house with a left arrow for Back to Home.
