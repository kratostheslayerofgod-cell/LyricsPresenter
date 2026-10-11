# Dark Spatial design handoff

Figma source: [Mobile Lyrics Presenter — Dark Spatial](https://www.figma.com/design/huTRIbqg23kIuOI8ucj3tJ)

## File structure

- `00 Foundations`: color, spacing, radius, elevation, typography, and motion tokens.
- `01 Components`: reusable button, status chip, text field, and glass panel sets with interactive states.
- `02 Product Screens — Phone + TV`: nine 412×915 phone screens, nine screen-specific error states, and four 1920×1080 receiver states. Connect → Library → Live → error → retry is linked as a prototype flow.

## Compose token mapping

| Token | Value | Compose mapping |
|---|---:|---|
| Background | `#07080C` | `SpatialColors.Background` |
| Base surface | `#10131C` | `SpatialColors.Surface` |
| Raised surface | `#171B27` | `SpatialColors.Raised` |
| Floating surface | `#202637` | `SpatialColors.Floating` |
| Main text | `#F5F6FF` | `SpatialColors.TextPrimary` |
| Secondary text | `#A4AAC0` | `SpatialColors.TextSecondary` |
| Muted text | `#737B94` | `SpatialColors.TextMuted` |
| Accent | `#7C8CFF` | `SpatialColors.Accent` / Material `primary` |
| Live / secondary | `#F2C879` | `SpatialColors.Gold` / Material `secondary` |
| Success / warning / error / info | `#66D2A5` / `#F2B76B` / `#F17C7C` / `#7FB9FF` | `SpatialColors` semantic values |
| Inner border / focus | white at 12% / `#9AA6FF` | `SpatialColors.InnerBorder` / `FocusBorder` |
| Spacing | 4, 8, 12, 16, 24, 32 dp | `SpatialSpacing.One` through `Eight` |
| Radius | 16, 24, 32 dp | `SpatialRadii.Control`, `Panel`, `Feature` |
| Elevation | 0, 12, 22 dp | `GlassElevation` in `GlassPanel` |
| Motion | 120, 220, 320 ms | Figma motion tokens; slide crossfade currently 180 ms |

## Reusable Compose components

- `GlassPanel`, `SpatialButton`, and `StatusChip` in `SpatialComponents.kt` share panel and control styling.
- `AppMessageHost` is the transient message surface. `AppMessages` converts ACK and media/network failures to short localized title/cause/action copy; no raw ACK JSON or exception text is shown.
- `LyricsControllerTheme` and `SpatialBackdrop` are the controller theme/backdrop. Blur is applied only on Android 12+ when the device is not low-RAM; otherwise a solid dark fallback is used.
- `LyricsReceiverTheme` keeps receiver chrome and system bars dark. Connected blank remains pure black; lyric changes crossfade without changing receiver state handling.

## Accessibility and implementation notes

- Live previous/next/black controls use larger targets and haptic feedback. Compose controls retain Material focus behavior; visible focus tokens are available for TV-facing components.
- The UI uses Android's system sans font so installed Telugu fallback remains available; the lyric line height retains font padding to reduce glyph clipping. Bundling Inter/Plus Jakarta Sans or Noto Sans Telugu assets remains a future asset/licensing choice.
- Component blur is intentionally limited to the ambient backdrop. Content cards use opaque dark fallback fills to keep text readable and avoid per-card blur cost.
- Bible UI/data was not present in the checked-in app source, so this visual pass adds its Figma screen and error state without inventing a new Bible feature or altering application logic.
