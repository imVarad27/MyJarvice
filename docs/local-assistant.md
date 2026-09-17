# Local assistant

Settings → Model & Intelligence → Local memory & documents contains the local library.

- Save a fact (up to 300 characters). Only facts explicitly saved here or with `Remember: ...` become persistent memories.
- Import a text-based PDF, UTF-8 TXT, or Markdown file. Limits: 20 documents, 5 MB per file, 50 PDF pages, 100,000 extracted characters per document. Scans need OCR; encrypted PDFs are not supported.
- Review saved facts and document names; Delete requires confirmation. Deleting a library entry does not erase the original file or past chat messages.

Chat commands work without loading the model and never go to the host:

```
Remember: I prefer short answers.
Show memories
Calculate (18 + 7) * 4
Search documents: physics examination
```

The calculator supports decimal numbers, unary signs, parentheses, and `+ - * /` with decimal64 precision. It rejects invalid expressions and division by zero; it never executes code.

Fast mode retrieves up to three matching passages or saved facts and supplies them to the on-device model. Retrieval uses length-normalized, rarity-weighted lexical matching, not semantic embeddings: specific keywords work best. Generic search words are ignored and exam/examination share a keyword. Common follow-ups such as “explain that” include the previous user topic in the retrieval query. The response includes a list of retrieved sources; that list is evidence supplied to the model, not proof that every generated claim is correct. Inspect the original text or use `Search documents:` for exact excerpts.

Strong sends the current request to the configured PC/server. Auto uses the connected host, or the local model when available offline. Saved library entries are not added to server payloads. The chat screen labels the route. Local errors are not automatically forwarded to a server.

## On-device agent harness

Phone mode and Auto's offline fallback now wrap the existing LiteRT model in a small, read-only tool loop. No Harness developer-platform dependency or extra model is installed.

Empty searches and tool failures return direct app-generated messages. They are not sent back to the model for speculative rewriting.

Clear arithmetic, date/time and saved-text search requests are routed deterministically before asking the model. Pure arithmetic and clock results are returned directly, with no model load or unreliable model rewrite. Saved-text results are supplied to the model for explanation. For other requests, the model can request one strict JSON tool call per turn: `calculate`, `search_library` (explicit memories/documents), `search_inbox` (saved text/OCR) or `clock` (phone date/time/timezone). The app validates the tool and arguments before running anything, then supplies the bounded result for a natural-language answer. The route label shows the current local stage, and replies list tools actually invoked and retrieved source names. Source lists are not a guarantee that generated claims are correct.

Try Phone mode with:

```
Use your calculator to work out 37 * 19.
Find the physics timetable in my saved documents and explain it briefly.
Find the product label I saved in my inbox.
Use the phone clock to tell me today's date.
```

The maximum is two tool calls and three model passes, counting pre-routed searches against the tool budget; repeated calls stop the loop. Unknown/malformed tool requests cannot execute, and failures are supplied as failures rather than invented successful results. Ordinary answers need one model pass. Model-selected tool use adds latency, and a small model may still ignore the protocol or calculate incorrectly without a tool; explicit `Calculate` and `Search documents:` commands remain available without inference. Query length is capped at 6,000 characters; each observation at 1,800 characters; recent history at six messages of at most 420 characters within a shared 1,800-character budget; each inference at 256 output tokens. Existing lexical retrieval supplies up to three 700-character excerpts.

Tools never send data to a PC or web service, read arbitrary paths, open URLs, execute code, place calls, send messages, create reminders, or alter memories. Inbox search does not listen to saved audio or understand an image beyond saved OCR text. Memory writes remain explicit existing commands, protected by the voice-action policy. The model still has no live web knowledge. Imported text and tool observations are marked as untrusted data; this reduces prompt-injection risk, not a guarantee against misleading model answers.

Local generation retains the CPU backend and synchronous LiteRT response API. Cancellation is checked between passes, but cannot immediately interrupt a blocking native inference. Requests and native model handoffs share a process-wide lock, avoiding concurrent chat/popup/benchmark models in memory. This improves practical usefulness; it does not retrain the phone model or turn it into a frontier model.

Validation:

```
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use manual tests on personal phones and `adb install -r` only, never uninstall/clear data or run Gradle connected instrumentation. Instrumentation must run on an emulator or a dedicated device with verified backups. The existing device smoke test needs `files/models/jarvis-on-device.litertlm`; its memory/PDF tests use an isolated library. Harness JVM tests inject fake inference/tools to check bounds, validation, observations and cancellation independently of acoustic or model quality.

2026-09-17 verification: debug build, 36 JVM tests, compile-only instrumentation and lint passed (warnings remain, no errors). Updated the RMX2061 with `adb install -r`; no app data was cleared and the installed model was retained. Initial actual-model tests showed that Gemma ignored the calculator protocol (37 × 19 → 687) and gave a vague response to an empty search. Added deterministic arithmetic/date/search routing and direct empty/error responses in response to those failures. Phone retests returned `37 * 19 = 703` with Calculator provenance and an honest missing-library result with Memory & documents provenance. These verify the deterministic safeguards, not reliable general model-selected multi-step planning. Positive document summarization, inbox/clock UI tests and more complex tool selection still need broader real-model checks. Synthetic smoke-test chats remain in local history; private conversations were not exported or deleted.

## Local model lab

Settings → AI & personal knowledge → Compare local models opens a private on-phone screen. Adding a candidate creates a new app-private file and never overwrites the working model. The first working model is retained as fallback; “Restore fallback” remains available. No test changes the active model or routing mode automatically.

Run the fallback first, then a candidate. Seven fixed synthetic prompts exercise formatting, arithmetic **without the harness calculator**, simple reasoning, extraction, unknown facts, excerpt-instruction resistance and conversational tone. Six have exact automatic checks; tone is manual review. Tests bypass personal history, saved knowledge and tools, use CPU, a fixed greedy sampler and 64 output tokens. Reports are stored only in app-private `model-benchmarks/` and do not become chats. Metadata invalidates reports after model-file changes. This is a small regression suite, not a comprehensive capability or security evaluation.

The screen reports whole-response elapsed time (the first test includes startup) and sampled whole-app PSS, **not** token throughput or guaranteed peak model memory. Loading is refused under Android low-memory conditions or below a conservative available-RAM estimate. The Qwen3-1.7B candidate, and renamed models of at least 900 MiB, require at least 3 GiB available. Other files require at least twice file size plus 384 MiB (minimum 768 MiB). These estimates reduce risk but cannot guarantee against native crashes/OOM. Each test taking over 45 seconds stops subsequent tests; this is not a native-call timeout. Stop/background navigation cancels after the current blocking call returns. Wake listening pauses during a comparison and resumes afterward.

“Use tested candidate” requires complete, current reports from the same device/backend/suite, a strictly higher automatic score, no failed runtime calls, each test within 45 seconds, and average completion time no more than 1.5× fallback. A candidate still needs your subjective tone review; passing this suite does not establish broad superiority. Chat loading applies the same RAM check, and local errors are never silently forwarded to the host.

Optional candidate staged on the connected RMX2061: [LiteRT Qwen3-1.7B](https://huggingface.co/litert-community/Qwen3-1.7B), INT4 block32 with FP32 activations, 977,184,032 bytes. Immutable revision `73fbc3fe8271c162a603ee66f6e7ed25b6211195`, SHA-256 `2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b`. PC and phone copies were verified. The conversion card reports 2,523 MB peak RSS on a newer Galaxy S26 CPU; that is not a prediction for this phone. Qwen3 prompts request the documented [`/no_think` soft switch](https://qwen.readthedocs.io/en/v3.0/inference/transformers.html); reasoning blocks are excluded from final text. The model is not bundled in the APK or committed to Git. The existing Gemma file and active choice remain unchanged.

Lab verification on 2026-09-17: 48 JVM tests, debug build, compile-only instrumentation and lint passed (0 errors; existing warnings remain). Updated with `adb install -r`. The model-only Gemma run completed all seven prompts: 3/6 strict automatic checks, average 3,377 ms including startup, largest sampled app PSS 1,107,832 KiB. It answered the arithmetic prompt incorrectly (`719`); extraction (`Tuesday at 15:00`) and its honest missing-birthday response failed the required literal output formats, not because they invented those facts. Its tone response was usable but one sentence rather than the requested two; review remains manual. Qwen's load was refused at 2.8 GiB available, below the 3 GiB gate; zero Qwen inferences ran, promotion remained disabled, and Gemma stayed active. Qwen runtime compatibility and comparative quality remain **unverified** until a safe run is possible. No personal chats or saved facts entered these tests, no screenshots were exported, and benchmark answers were stored only as lab reports.

Final phone UI checks confirmed a fully visible 48 dp run/stop touch target above the vendor navigation region (text bounds 2037–2091 px, button 1992–2136 px). A manual Stop during native inference saved an incomplete stopped report, returned to an enabled Run control, and did not leave the UI stuck in “Stopping”. A complete baseline was rerun afterward. Existing history and the original Gemma file remained present, with no app-data clear. No screenshots were needed for these geometry/status checks.
