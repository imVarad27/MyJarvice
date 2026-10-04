# Jarvis UI system

Jarvis uses Material 3 as the interaction baseline and adds a restrained assistant identity. The interface should feel calm, fast, and trustworthy rather than decorative.

## Product principles

1. **One obvious next action.** Each surface has one primary action; secondary actions use tonal or icon buttons.
2. **State is visible.** Phone/PC routing, connection, listening, thinking, and paused states are shown next to the control they affect.
3. **Content before chrome.** Conversation, tasks, and saved items use most of the screen. Navigation remains compact.
4. **Progressive disclosure.** Advanced settings and the full tool catalog stay behind expandable groups or sheets.
5. **Safe by design.** Destructive actions remain visually distinct and retain confirmation where required.
6. **Accessible by default.** Interactive targets are at least 48 dp, icons have semantic labels, layouts reflow with larger text, and color is never the only status signal.

## Tokens

- Spacing uses a 4 dp base scale. Screen gutters are 20 dp; compact controls use 8–12 dp; cards use 16–20 dp.
- Shape roles: controls 14 dp, cards 18–24 dp, hero/popup surfaces 28–32 dp, status and icon containers are circular.
- The Pixel style uses indigo actions, neutral surfaces, and cyan status accents.
- The Jarvis style uses cyan actions and amber tertiary accents while keeping the same neutral surfaces.
- AMOLED changes only background/container luminance; it does not change hierarchy or interaction behavior.
- Typography uses the Android system family for performance and language coverage. Weight and spacing—not multiple typefaces—create hierarchy.

## Component rules

- Use Material icons for standard actions. Do not represent actions with glyph text such as `×`, `›`, `+`, or `⌄`.
- Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes`; do not add screen-specific hex colors.
- Use `JarvisBrandMark`, `JarvisPageHeader`, and `JarvisIconBadge` for shared brand and navigation treatment.
- Prefer `Surface`, Material buttons, and Material text fields so pressed, focused, disabled, and accessibility states remain consistent.
- Keep the chat composer and model selector reachable at the bottom; model routing must remain visible before sending.

## Screen hierarchy

- **Today:** compact greeting, three glanceable metrics, four quick actions, then tasks, reminders, calendar, recent work.
- **Chat:** compact app bar, readable conversation column, attachment preview, one consolidated composer.
- **Saved:** search and filters, scannable cards with type icons, full-screen detail.
- **Settings:** search first, current assistant state second, collapsible categories with recognizable icons.
- **Voice:** status and transcript are central; info/share/voice controls are quiet; mute and close remain large.
- **Popup:** one compact assistant surface with the same model selector and composer semantics as full chat.

## Figma mapping

When a Figma connection is available, map Figma variables to these Compose roles instead of copying raw values:

| Figma role | Compose role |
| --- | --- |
| Brand / action | `primary`, `onPrimary`, `primaryContainer` |
| Status / local | `tertiary`, `tertiaryContainer` |
| Canvas | `background`, `surface` |
| Cards | `surfaceContainerLow` through `surfaceContainerHighest` |
| Dividers | `outlineVariant` |
| Main and secondary text | `onSurface`, `onSurfaceVariant` |

For a Figma-driven implementation, fetch the exact node design context and screenshot before changing code, then validate the result against the screenshot. The canonical platform references are [Material 3 foundations](https://m3.material.io/foundations/) and [Android Compose app bars](https://developer.android.com/develop/ui/compose/components/app-bars).
