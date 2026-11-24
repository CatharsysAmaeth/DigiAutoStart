# Development diagnostics

Diagnostic collection is intended only for internal development and device testing.

- Diagnostic tools are available only in Debug builds.
- Collection is opt-in and is not part of normal application operation.
- The normal Release contains no diagnostic upload implementation, diagnostic screen or private test-updater code.
- Diagnostic reports must never be published in the public repository. They may contain device, software, timing or usage-related information even when obvious identifiers are omitted.

Do not commit diagnostic reports, credentials, signing material, private service locations or personal/network information.
