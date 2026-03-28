# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest (main branch) | ✅ Yes |
| Older releases | ❌ No |

## Reporting a Vulnerability

If you discover a security vulnerability in Hazel, **do NOT open a public issue.**

Use GitHub's built-in [Private Vulnerability Reporting](https://github.com/SibtainOcn/Hazel/security/advisories/new) to report it securely.

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Affected component (Android / Windows)
- Potential impact

### Response Timeline

| Action | Timeframe |
|--------|-----------|
| Acknowledgment | Within 48 hours |
| Initial assessment | Within 7 days |
| Fix release | Depends on severity |

## Scope

### In Scope
- Script injection via malicious URLs
- Unauthorized file system access beyond Download folder
- Credential or token exposure in logs or temp files
- Dependency vulnerabilities (yt-dlp, FFmpeg)

### Out of Scope
- Issues in third-party tools (yt-dlp, FFmpeg) — report those upstream
- Social engineering attacks
- Denial of service on local machine

## Security Design

- **No credentials stored** — Hazel does not handle user accounts or tokens
- **Minimal permissions** — only READ storage for music library, no MANAGE_EXTERNAL_STORAGE
- **No telemetry** — no data is collected or transmitted
- **In-app updates** — APK downloads verified via GitHub Releases API

## Keeping Dependencies Updated


App updates are delivered via GitHub Releases with in-app download and install.
