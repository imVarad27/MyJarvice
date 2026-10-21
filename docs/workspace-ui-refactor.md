# Conversation workspace refinement

Local build 1.2.2 / 5, October 17, 2026. Builds on the existing pending chat-first
home/voice changes without discarding those edits or changing model intelligence.

## Design choices

- Keep empty home free of dashboards and suggestion tiles. Use native type and
  a restrained visual hierarchy rather than additional animations or claims.
- Theme the whole app consistently: warm neutral light surfaces, charcoal dark,
  true-black AMOLED. Preserve existing style/dynamic-color preferences. Palette
  tests check enabled static body/supporting text, errors and primary actions;
  Android-generated dynamic schemes still require device checks.
- Use neutral user bubbles up to 86% of the reading column (maximum 520 dp),
  quieter wrapping assistant provenance labels and 16/26 sp answer typography.
  Preserve visible error, safety, phone/PC and streaming distinctions.
- Keep one composer with visible model choice, attachments, dictation and send/
  voice. Focus changes only its outline. The primary action is neutral and high
  contrast. Selector text has bounded width so it cannot push controls off-screen.
- Extract the sidebar from MainScreen into a dedicated component. Navigation,
  new chat and search scroll; Settings stays pinned. Keep title/message search
  local without showing message snippets. Delete/clear callbacks still lead to
  MainScreen's existing confirmation flow; no deletion occurs on opening a menu.
  Switching, creating and deleting chats are disabled with a visible explanation
  while a reply is in progress; this mirrors existing handler guards.
- Use tool-specific Material icons without modifying shortcut commands, route
  eligibility, pause handling or any authorization policy.
- Fenced code is literal selectable text, with language metadata, horizontal
  scrolling and explicit copy. Extremely wide lines wrap at a bounded paragraph
  width to avoid invalid Android layouts. No HTML, link or command execution is introduced.
  Partial code cannot use Copy code. Whole-reply copy and approved sharing still
  use original content, not the formatted display. Code extraction normalizes
  CRLF to LF for display/copy and removes fence delimiters; not a byte-for-byte
  file export. This is a small Markdown subset, not a complete CommonMark renderer.

## References and access limits

The requested [ChatGPT homepage](https://chatgpt.com/) exposes new chat, search,
attachments and a central conversational entry. [OpenAI UI guidelines](https://developers.openai.com/plugins/concepts/ui-guidelines),
consulted with the OpenAI Docs skill, inform native system typography, restrained
color, clear hierarchy and accessibility. Those guidelines concern partner UI,
not an exact Android-app specification.

The supplied private Claude and Gemini conversation URLs were access-gated.
[Claude's public entry](https://claude.ai/) redirected to login;
[Gemini's public entry](https://gemini.google.com/) exposed no usable detailed
layout. [DeepSeek's public homepage](https://www.deepseek.com/en/) was accessible,
but its chat interface was not retrievable. No private conversation text, account
state or protected screenshot was copied. These are product-direction references,
not proof of current pixel-level similarity to all four products.

## Verification and remaining checks

Unit tests cover code-block boundaries, unfinished streams, literal content,
Windows line endings and static-theme contrast. Synthetic Compose tests cover
explicit code copy, disabled streaming copy, sidebar search/menu callbacks and
short/narrow large-text navigation. Existing permission/approval tests remain.
Compile-only UI tests do not establish actual layout fidelity or recognizer
accuracy. No emulator is installed in the configured SDK; visual, keyboard,
landscape, screen-reader, dynamic theme and real-device checks remain necessary.
Never run uninstalling instrumentation or export private personal-phone content.

No PC/server process, model, prompt, provider credential, permission, backup,
enrollment or saved-data schema is changed by this refinement. A UI request does
not authorize pushing or installing; those are separate explicit actions.
