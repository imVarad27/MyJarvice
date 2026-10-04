# Model-selected capabilities

The chat pipeline no longer executes actions because a keyword occurs in the user's text. Ollama receives native function schemas; the phone's LiteRT model receives a bounded JSON protocol. The model chooses the capability, while ordinary code enforces the capability allowlist, argument validation and permissions. System instructions and tool schemas remain necessary; removing them would remove safety and reliable API contracts, not make the model smarter.

## PC capabilities

- Read phone telemetry supplied by Android; read PC telemetry.
- Open allowlisted PC apps or standard folders; set an explicit volume percentage; control media.
- Read/add/complete PC tasks; read/create PC reminders using a future ISO timestamp with a timezone.
- Read/save personal facts, preferred name and email contacts.
- Search indexed PC documents or public web evidence with sources.
- Prepare an email draft or Android call/WhatsApp review flow. No send function is exposed to the model.

PC task/reminder/memory stores remain separate from the phone's offline stores. Reminders created on the PC require the host server to run for delivery. A model-selected reminder time may still be wrong: the saved timestamp is shown so it can be checked.

App launches use a fixed capability allowlist and no raw shell-command fallback. Volume errors now report failure rather than fabricated success. Low-risk controls and explicit personal memory/task writes execute without a separate confirmation; call/message/email preparation still enters review. Automated PC screenshot sharing, locking, arbitrary shell commands, reminder deletion and chat-triggered file indexing are deliberately not exposed. The old demo light state is no longer reported as real hardware.

## Bounded execution and approvals

The host permits one native tool call at a time, up to three calls/four model passes. Invalid arguments, unknown or parallel calls, and repeated calls stop without further execution. Writes return the executor's outcome and end the turn. There is no natural-language fallback that runs commands when the model is unavailable.

Email drafting accepts typed recipient/subject/body fields. Recipient and header validation run before a durable draft is created, even if SMTP is not configured. Only the existing expiring, one-use approval ledger can authorize a send through the explicit app approval button. The model cannot call that approval handler. Phone-directed calls and WhatsApp preparation still pass Android validation and its confirmation dialog.

The server records completed/prepared/blocked/failed/rejected tool outcomes in Activity, without storing the tool payload in the audit summary. A prepared Android action is not proof that Android executed it. Exceptions stop rather than retry a possibly completed action.

Android now reports executor failures in chat instead of playing a success chime for a failed launch. Host-selected timers use the same executor as local timers. Alarm/timer arguments are parsed as bounded parameters, not user intent: missing units, ambiguous alarm times and invalid ranges are rejected rather than defaulting silently to 7 AM or ten minutes. Clock UI remains visible for review.

## Voice and untrusted content

Protected, unverified voice sessions receive read-only schemas. Validation also blocks any write returned despite the restricted schema. The phone enforces the same rule for local tools and incoming host phone actions. Photo/OCR questions never receive action execution rights.

After reading saved tasks, notes, documents or web content, the host blocks later state-changing tools and external searches in that turn. This prevents a retrieved instruction from chaining directly into a write or leaking retrieved content through a web query. A fresh direct request is required for a write. This intentionally limits “read then act” automation, and is not a comprehensive prompt-injection or data-loss-prevention guarantee. Web search queries reach external providers; do not submit private information in them.

## Runtime and tests

Ollama context defaults to 8,192 tokens for the larger tool registry; environment overrides remain supported. Recent history is capped at six messages of 1,200 characters each. Host observations are capped at 6,000 characters, and source lists at 12. Native tool decisions currently use complete responses, not token streaming; read-tool requests need an additional model pass, so latency can exceed ordinary chat. The plain/photo generation path still supports streaming.

Run `python -m unittest discover -s server -p 'test_*.py'` from the project root. Server integration tests use temporary assistant databases and mocked network/OS operations. Importing the server no longer starts personal-file indexing, voice pre-caching or reminder watchers: they start in the FastAPI lifespan.

Run `python server/eval_tool_selection.py` manually against the installed Ollama model. It checks synthetic paraphrases and negative cases and never executes the selected tools. Passing this small suite is not evidence of general autonomous reliability. Phone acoustic checks and real local-model tool selection still need manual device testing.
