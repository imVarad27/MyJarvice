# Small-context code navigation with Graphify

The existing local `graphifyy` CLI (verified version 0.9.90) is used as a developer
tool, not an Android dependency or an assistant model. No APK/version bump is
needed for this integration. Pending UI edits are preserved and indexed from the
working tree, not falsely described as pushed commits.

## Refresh and query

From the repository root in PowerShell:

```powershell
graphify extract . --code-only --no-cluster --max-workers 2
graphify query "VoiceModeScreen" --budget 1000
graphify query "retryVoiceListening" --context call --budget 1000
graphify query "MainScreenViewModel" --seed "MainScreenViewModel" --budget 1000
```

`AGENTS.md` directs future Codex turns to use bounded graph queries for structural
exploration, followed by relevant source ranges. Exact text/file searches still
use `rg`. There is no guarantee Codex will always choose Graphify, or that each
task will consume fewer tokens. Do not compare small graph answers with reading
the entire repository and present that as measured Codex token savings.

## Scope and privacy

- `.graphifyignore` is a strict allowlist: Kotlin/Java source and tests under
  `app/src`, root/app Gradle scripts, and root-level server Python source/tests.
- Assets, model weights, credentials, local properties, runtime databases,
  caches, build products, screenshots, documents and media are outside the scope.
- Extraction uses local syntax parsing only. `--no-cluster` avoids community
  labeling/model calls and produces a raw graph suitable for CLI queries. There
  is no API key requirement, semantic extraction, embedding service or cloud upload.
- `graphify-out/` is git-ignored. Its graph/manifest/cache may contain source
  names and code-derived relationships; don't publish it or load it wholesale.
- No global graph, global Codex configuration, new skill installation, watchers,
  Git hooks, scheduled jobs, remote registration or package upgrades are performed.

## Freshness and limits

Refresh after Kotlin/Python changes, additions, deletions or moves, and before
relying on architecture answers when unsure of freshness. Graphify's incremental
manifest is local and may conservatively refuse a suspiciously smaller rebuild.
If a refresh fails, don't force overwrite blindly or trust old results: inspect
the cause and use scoped source search instead.

Queries are symbol/dependency retrieval, not general natural-language reasoning.
Syntax graphs may miss reflective calls, Android intents, Compose lifecycle
relationships, dynamic Python dispatch, permissions and runtime routes. Edges
tagged inferred are not verified behavior. Verify against current source and
tests before modifying safety-sensitive code. Small exact searches can be more
efficient than graph traversal; use the right tool for the question.

The token budget is approximate, not a strict output ceiling; Graphify may retain
all edges when every node fits, or print a truncation warning. Prefer a narrower
symbol/context rather than automatically increasing it. Never print the raw
minified `graph.json`, including with a line-based head command.

## Verified local baseline

- 185 code files; 2,180 nodes and 5,601 edges. All recorded source paths were
  checked against the allowlist; no out-of-scope source paths were found.
- `VoiceModeScreen` and call-filtered `retryVoiceListening` queries returned
  source locations, including the pending UI refactor. The latter returned eight
  nodes and seven edges instead of reading entire source files.
- A second extraction reported 185 unchanged files and skipped rebuilding.
- Parser warnings affected 10 files (partial syntax extraction); seven files
  produced no symbols, including Gradle scripts and theme constants. These are
  coverage limitations of this tool, not evidence the Android build is broken.
  Source search remains necessary. No actual Codex token-saving percentage was
  measured, and no semantic graph coverage of project documentation is claimed.

Primary reference checked October 14, 2026:
[Graphify source and usage](https://github.com/Graphify-Labs/graphify).
