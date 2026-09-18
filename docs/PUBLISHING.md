# Publishing the project safely

The repository should contain source, build configuration, the Gradle wrapper, the synthetic migration fixture, AGENTS.md, the build skill and the reviewed guides. A Git push sends committed history; the contents of ignored local folders are not uploaded by a normal push.

## Keep local

- `.tools/`: private debug keystore, local toolchains, caches and backups. Back up the original signing key privately so future APK updates can preserve installed app data.
- `local.properties`, `.gradle/` and `.idea/`: machine-specific SDK/IDE state.
- `build/`, module `build/` and `bin/` folders, `.class`, APK/AAB files, logs and captures: generated output.
- `artifacts/`: installable debug APKs, source archives, screenshots, logs and reports. Review any file separately before attaching it to a GitHub release; ignored files can still be uploaded manually.
- Environment/credential files and private keys: never commit live values. Git ignore rules cover common names and extensions, but cannot recognize every possible secret filename.

`gradle.properties` is safe to track while it contains only the current JVM/Android settings. Keep repository credentials out of it. The synthetic `engine/src/test/resources/legacy-v02.bin` contains no real usage data and is required by migration tests. The Gradle wrapper JAR is required to build from a clone.

## Review before committing

Run these commands from the repository root:

```sh
git status --short
git diff --check
git diff --cached --stat
git diff --cached
```

Stage only reviewed changes. Do not use force-add to bypass ignored key, cache or artifact files. Verify `git ls-files` does not include generated `bin/` files or private signing/configuration material. The reviewed docs are explicitly allowed through `.gitignore`; any additional document needs its own review.

For a local secret scan using Gitleaks 8:

```sh
gitleaks git . --log-opts="--all" --redact --no-banner
```

Scan changed and untracked candidate files too: the Git-history command does not cover uncommitted content. Avoid running a directory scan over toolchains and caches; export only the files proposed for publication to a temporary folder and scan that folder with `gitleaks dir`. Keep scan reports local and redacted. A clean scanner result is evidence from its detection rules, not a guarantee that no sensitive content exists.

## History and privacy

Removing a secret from the latest file or adding an ignore rule does not remove earlier copies from history. If a credential was committed, revoke or rotate it before considering history cleanup. History rewriting affects other clones and requires a separate coordinated decision.

Commit metadata includes the author's name and email. Older revisions may contain personal workstation paths or file-manager metadata even when the latest files are sanitized. Review those privacy details before making an existing repository public; a future commit does not erase them.

This checklist concerns accidental publication of local information. It is not a penetration test or a dependency-vulnerability assessment of the Android application.
