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

Fast mode retrieves up to three matching passages or saved facts and supplies them to the on-device model. Retrieval uses word overlap, not semantic embeddings: specific keywords work best. The response includes a list of retrieved sources; that list is evidence supplied to the model, not proof that every generated claim is correct. Inspect the original text or use `Search documents:` for exact excerpts.

Strong sends the current request to the configured PC/server. Auto uses the connected host, or the local model when available offline. Saved library entries are not added to server payloads. The chat screen labels the route. Local errors are not automatically forwarded to a server.

Local generation retains the CPU backend and synchronous LiteRT response API. Responses are capped at 256 output tokens and recent history is limited to four messages to keep the prompt bounded. Multiple local requests cannot run concurrently. This improves usefulness and stability; it does not turn the phone model into a frontier model.

Validation:

```
gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.myjarvice.data.LocalAssistantDeviceTest com.example.myjarvice.test/androidx.test.runner.AndroidJUnitRunner
```

The device smoke test needs `files/models/jarvis-on-device.litertlm` already installed. Its memory/PDF tests use a temporary isolated library; the generation test makes two short local requests.
