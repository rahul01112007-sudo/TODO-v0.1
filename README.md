# TODO — v0.1

Private, local-first Android AI assistant.

## Current version
- Text chat UI
- No network permission
- No API key
- No Firebase
- No voice
- Local-only demo responses
- Architecture ready for an on-device LLM and Android tools

## Next versions
1. Add an on-device quantized LLM.
2. Add local persistent memory.
3. Add a tool/action layer for supported Android operations.
4. Add local document/file search.
5. Add permission-controlled phone integrations.

## Privacy
The Android manifest intentionally contains no INTERNET permission. The app cannot make network requests unless a future version explicitly adds network access.
