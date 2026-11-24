# Development diagnostics

Diagnostic collection is intended only for internal development and device testing.

- Diagnostic tools are available only in Debug builds.
- Collection is opt-in and is not part of normal application operation.
- The normal Release contains no diagnostic upload implementation, diagnostic screen or internal test-update tools.
- Diagnostic reports must never be published in the source repository. They may contain device, software, timing or usage-related information even when obvious identifiers are omitted.

Do not commit diagnostic reports, credentials, signing material, internal service locations or personal/network information.
