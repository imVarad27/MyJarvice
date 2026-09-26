# Jarvis UI review — September 2026

## Direction

One recognizable Jarvis identity: an orbital J mark, warm light surfaces, quiet dark surfaces, teal emphasis, rounded controls, and a readable text hierarchy. Pixel and Jarvis accent choices, system/light/dark/AMOLED themes, and wallpaper colors remain available.

References consulted: [Google's Gemini visual design](https://design.google/library/gemini-ai-visual-design) for consistent shapes and identity, and [Material layout examples](https://m3.material.io/foundations/layout/canonical-examples/overview) for responsive structure. These inform the design; Jarvis uses its own mark and product behavior.

## Screen review

| Surface | Finding and change |
| --- | --- |
| Chat and history | Add the shared brand mark, constrain the reading/composer width on larger screens, improve surface hierarchy and reply action labels. Preserve search, attachments and conversation controls. |
| Composer and tools | Give the input its own raised surface, make tool rows easier to scan, and visibly distinguish unavailable PC tools. Keep the model picker next to the input. |
| Model picker | Use a bordered selected state and larger explanatory text, with readiness and privacy details retained. |
| Settings | Add category search, an explicit no-results state, clearer section headers, descriptive subtitles and more readable secondary copy. |
| Saved inbox and detail | Center a bounded reading column, add a branded empty state, and remove repeated “Open item” labels from cards that are already clickable. |
| Voice | Use theme colors for the orb, reduce toolbar crowding, and center the two primary controls. Recognition and permission behavior are unchanged. |
| Voice enrollment | Share the page header, use theme-aware recording/success accents, and allow the final button to grow with text. |
| Assistant popup | Use the same Jarvis brand mark as the main app and launcher. Retain voice state, model selection, close and expand controls. |
| Memory and documents | Use an outlined surface, a descriptive title, and a recognizable destructive-action color. |
| Model comparison | Replace the developer-facing lab title with a consistent page header. Keep benchmark results and promotion criteria intact. |
| PC files | Replace emoji action labels, enlarge secondary text and shortcut targets, and stack file actions so labels have room. |
| Email approval | Remove hard-coded dark colors and monospace styling; display the complete selectable draft in a scrolling preview, with send approval always explicit. |
| Voice dialogs and PC pairing | Use sentence-case titles and readable connection state; allow pairing content to scroll above the keyboard. |
| Share receiver | Add the common brand mark and scrollable content for smaller displays and large text. |
| Welcome and splash | Replace competing emblems, the incorrect JARVIC wordmark, animated typing and unsupported version copy with the shared identity. Normal launch still opens chat directly. |
| Android launcher | Replace detailed reactor artwork with a simple vector J. Supply adaptive/themed support and a vector fallback for Android 7. |

## Next features, in priority order

1. **Today:** real due reminders and saved items in one editable view; add calendar access only when the user enables it. Start with the existing on-phone inbox/reminder data.
2. **Action receipts:** show what Jarvis actually opened or completed, a clear failure explanation, and undo where the operation supports it. This also needs executor results rather than optimistic success messages.
3. **Backup and restore:** export chats, memories and saved items with previewed restore and explicit treatment of private model files.
4. **Quick capture widget:** one-tap note, photo or voice capture into the existing inbox, plus a shortcut to voice chat.
5. **Voice-note transcription:** make shared recordings searchable, with an explicit phone/PC processing choice and a progress indicator.

These are recommendations, not integrations implemented in this UI pass. Better tools and retrieval help a small local model, but do not make its general reasoning equal to a frontier model.

## Validation scope

Run APK assembly, existing JVM tests, Android lint and compile-only instrumentation. Android Studio previews cover blank chat, welcome, voice and the brand in light/dark and large-text configurations. Previews are review entry points, not proof of successful rendering or on-device interaction.

No Android device was attached during the initial check. Live layout, keyboard, TalkBack, launcher masks, and gesture-navigation checks remain pending a device or emulator. On the personal phone use only an in-place update; never run instrumentation that uninstalls the app or clears its data.

Final automated verification: debug APK assembly, all 51 JVM tests, compile-only Android UI tests, and Android lint completed successfully. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. No device installation or screenshot capture was performed.
