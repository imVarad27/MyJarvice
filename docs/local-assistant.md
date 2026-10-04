# Local assistant

Settings → AI & personal knowledge contains the phone's private memory, imported documents and model lab.

- Explicitly save a fact (up to 300 characters, maximum 50 facts), or ask the phone model to remember it.
- Import a text-based PDF, UTF-8 TXT or Markdown file. Limits: 20 documents, 5 MB per file, 50 PDF pages and 100,000 extracted characters per document. Scans need OCR; encrypted PDFs are unsupported.
- Deleting a library entry requires confirmation and does not erase the original file or past chats.

## Model-selected tools

Phone mode and Auto's offline fallback use the existing LiteRT model in a bounded capability loop. There are no keyword/preflight command shortcuts: the model decides whether a tool is needed, then the app validates its name and arguments.

Available tools: calculator; phone date/time; phone status; saved library/inbox search; list/save memory; list/add phone tasks; app launcher; navigation; flashlight; alarm and timer preparation. Calls, messages, payments, deletion, shell commands, arbitrary paths and web access are unavailable in the local loop. Normal conversation should use no tool.

Try natural requests, rather than a required command syntax:

- “Could you check how much charge my phone has left?”
- “Put buying groceries on my to-do list.”
- “Find the physics timetable in my saved documents.”
- “Work out 37 times 19 using the calculator.”

The loop permits two tool calls and three model passes. Repeated or malformed requests stop safely. Tool failures/empty searches return an honest app-generated result without speculative model rewriting. Writes finish with the executor's result, so they cannot be repeated by another model pass. Tool observations are bounded to 1,800 characters; input to 6,000 characters; history to six short messages within a shared 1,800-character budget; each inference to 256 output tokens.

Voice protection checks the selected capability, not words in the transcript. An unverified protected voice session can answer questions and use read-only tools, but cannot change memory/tasks or control the device. Photo/OCR requests are read-only. Pause is checked immediately before each tool. After reading saved content, later writes/device controls are blocked for that turn; a fresh direct request is needed. These guards reduce prompt-injection risk; they do not guarantee accurate model answers or secure speaker authentication.

Search is length-normalized, rarity-weighted lexical retrieval, not semantic embeddings. Model-selected search retrieves matching excerpts and supplies source names. Sources show what was retrieved, not proof of every generated claim. Imported text, saved facts and observations are untrusted information, not instructions. Saved audio is not transcribed by inbox search, and images are searchable only through saved OCR.

Phone mode uses the CPU backend for this device's driver compatibility. Native inference cannot be immediately interrupted; cancellation is checked between passes. A process-wide lock prevents overlapping native model loads. The model must load even for simple tool requests, and may ignore a tool or select it incorrectly. This implementation improves access to reliable capabilities; it does not retrain the model or make it equivalent to a frontier model.

## PC versus phone data

Connected PC uses the configured Ollama host; Auto prefers a reachable host, otherwise the installed phone model. Errors are not silently forwarded between routes. Phone knowledge and task stores are separate from PC memory/tasks and are not uploaded automatically.

The PC tool loop supports model-selected PC controls, tasks, reminders, memory, web/document search and email drafts. See [Model-selected tools](model-selected-tools.md) for capability limits and approval rules.

## Validation and installation

Run JVM tests, build and lint with:

`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

On a personal phone use manual checks and `adb install -r` only. Never uninstall, clear data or run connected Gradle instrumentation on a personal device. Instrumentation requires an emulator or dedicated test device with verified backups.

## Local model lab

Settings → AI & personal knowledge → Compare local models opens a private on-phone screen. Adding a candidate creates a new app-private file and never overwrites the working model. The first working model is retained as fallback; “Restore fallback” remains available. No test changes the active model or routing mode automatically.

Run the fallback first, then a candidate. Seven fixed synthetic prompts exercise formatting, arithmetic **without the harness calculator**, simple reasoning, extraction, unknown facts, excerpt-instruction resistance and conversational tone. Six have exact automatic checks; tone is manual review. Tests bypass personal history, saved knowledge and tools, use CPU, a fixed greedy sampler and 64 output tokens. Reports are stored only in app-private `model-benchmarks/` and do not become chats. Metadata invalidates reports after model-file changes. This is a small regression suite, not a comprehensive capability or security evaluation.

The screen reports whole-response elapsed time (the first test includes startup) and sampled whole-app PSS, **not** token throughput or guaranteed peak model memory. Loading is refused under Android low-memory conditions or below a conservative available-RAM estimate. The Qwen3-1.7B candidate, and renamed models of at least 900 MiB, require at least 3 GiB available. Other files require at least twice file size plus 384 MiB (minimum 768 MiB). These estimates reduce risk but cannot guarantee against native crashes/OOM. Each test taking over 45 seconds stops subsequent tests; this is not a native-call timeout. Stop/background navigation cancels after the current blocking call returns. Wake listening pauses during a comparison and resumes afterward.

“Use tested candidate” requires complete, current reports from the same device/backend/suite, a strictly higher automatic score, no failed runtime calls, each test within 45 seconds, and average completion time no more than 1.5× fallback. A candidate still needs your subjective tone review; passing this suite does not establish broad superiority. Chat loading applies the same RAM check, and local errors are never silently forwarded to the host.

Optional candidate staged on the connected RMX2061: [LiteRT Qwen3-1.7B](https://huggingface.co/litert-community/Qwen3-1.7B), INT4 block32 with FP32 activations, 977,184,032 bytes. Immutable revision `73fbc3fe8271c162a603ee66f6e7ed25b6211195`, SHA-256 `2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b`. PC and phone copies were verified. The conversion card reports 2,523 MB peak RSS on a newer Galaxy S26 CPU; that is not a prediction for this phone. Qwen3 prompts request the documented [`/no_think` soft switch](https://qwen.readthedocs.io/en/v3.0/inference/transformers.html); reasoning blocks are excluded from final text. The model is not bundled in the APK or committed to Git. The existing Gemma file and active choice remain unchanged.

Lab verification on 2026-09-17: 48 JVM tests, debug build, compile-only instrumentation and lint passed (0 errors; existing warnings remain). Updated with `adb install -r`. The model-only Gemma run completed all seven prompts: 3/6 strict automatic checks, average 3,377 ms including startup, largest sampled app PSS 1,107,832 KiB. It answered the arithmetic prompt incorrectly (`719`); extraction (`Tuesday at 15:00`) and its honest missing-birthday response failed the required literal output formats, not because they invented those facts. Its tone response was usable but one sentence rather than the requested two; review remains manual. Qwen's load was refused at 2.8 GiB available, below the 3 GiB gate; zero Qwen inferences ran, promotion remained disabled, and Gemma stayed active. Qwen runtime compatibility and comparative quality remain **unverified** until a safe run is possible. No personal chats or saved facts entered these tests, no screenshots were exported, and benchmark answers were stored only as lab reports.

Final phone UI checks confirmed a fully visible 48 dp run/stop touch target above the vendor navigation region (text bounds 2037–2091 px, button 1992–2136 px). A manual Stop during native inference saved an incomplete stopped report, returned to an enabled Run control, and did not leave the UI stuck in “Stopping”. A complete baseline was rerun afterward. Existing history and the original Gemma file remained present, with no app-data clear. No screenshots were needed for these geometry/status checks.
