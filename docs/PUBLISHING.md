# Publishing the project safely

The repository should contain source, build configuration, the Gradle wrapper, the synthetic migration fixture, AGENTS.md, the build skill and the reviewed guides. A Git push sends committed history; the contents of ignored local folders are not uploaded by a normal push.

## Keep local

- `.tools/`: private debug keystore, local toolchains, caches and backups. Back up the original signing key privately so future APK updates can preserve installed app data.
- `local.properties`, `.gradle/` and `.idea/`: machine-specific SDK/IDE state.
- `build/`, module `build/` and `bin/` folders, `.class`, APK/AAB files, logs and captures: generated output.
- `artifacts/`: installable debug APKs, source archives, screenshots, logs and reports. Review any file separately before attaching it to a GitHub release; ignored files can still be uploaded manually.
- Environment/credential files and private keys: never commit live values. Git ignore rules cover common names and extensions, but cannot recognize every possible secret filename.

`gradle.properties` is safe to track while it contains only the current JVM/Android settings. Keep repository credentials out of it. The synthetic `engine/src/test/resources/legacy-v02.bin`, `legacy-v04-*.bin` and `legacy-v05-*.bin` fixtures contain no real usage data and are required by migration tests. The Gradle wrapper JAR is required to build from a clone.

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


## GitHub build and test pipeline

`.github/workflows/ci.yml` runs on pushes to `main`, `v*` tags, pull requests targeting `main`, and manual runs from Actions.

1. The timing jobs compile with Java 17 source compatibility and run the complete scenario/reference-model suite on JDK 17 and 21. The console logs are uploaded even when a test fails.
2. Once both timing jobs pass, the Android job installs SDK 36 and Build-Tools 36.0.0, validates the Gradle wrapper, builds the debug APK, and runs Android lint on JDK 21.
3. Build/lint reports are retained for 14 days; the disposable CI APK is retained for 7 days. A tag build fails if its name does not match `versionName`.

Actions are pinned to full verified commit SHAs. Tokens have read-only repository permission; pull requests receive no signing material. Only pushes to `main` write the Gradle cache. Updating action versions is a reviewed workflow change.

The `SocialPause-ci.apk` artifact uses a newly generated runner debug key. It is useful on a test emulator but cannot update the existing personal installation. Keep that installation and its data; use the release's `SocialPause.apk` for updates. No private signing key is stored in GitHub secrets or workflow artifacts.

## Prepare a release locally

The GitHub release uses the existing personal debug signing identity, not a newly generated Play Store/release key. Its public certificate fingerprint is checked against `.github/release-signing.sha256`. Keep the corresponding `.tools/android-user/debug.keystore` backed up privately. Do not edit the fingerprint to bypass a signing mismatch.

From the repository root:

1. Update `versionName` and increment `versionCode` for an app update. Keep 0.6.0/code 8 for this first publication of the already shipped version.
2. Add reviewed notes at `.github/release-notes/vX.Y.Z.md`. Commit the source, workflows and notes; the packaging helper requires a clean tree.
3. Use the same complete JDK and SDK as the Android Studio guide. Run:

```sh
python3 scripts/package-release.py v0.6.0
```

The helper runs `scripts/verify.sh` and rejects a missing original key, mismatched version/certificate, changed source during the build, or an existing tag pointing elsewhere. It packages into `artifacts/releases/v0.6.0/`:

- `SocialPause.apk`: verified APK using the original signer.
- `SocialPause-source.zip`: source from the exact Git commit, with no ignored local files.
- `BUILD-INFO.txt`: commit, version and public signing fingerprint.
- `SHA256SUMS.txt`: checksums of those three assets.

`build.log` remains local for troubleshooting; it is not a release asset. Validate the checksums on macOS with:

```sh
cd artifacts/releases/v0.6.0
shasum -a 256 -c SHA256SUMS.txt
```

## Publish after GitHub sign-in

Git pushes use SSH, but creating a release/uploading assets also needs GitHub CLI authentication. Sign in interactively; never paste a token into source or release notes:

```sh
gh auth login --hostname github.com --git-protocol ssh --web --skip-ssh-key
```

From the repository root, push the reviewed commit and wait for **Build and test** to pass for that exact commit. Tag it without moving any existing tag:

```sh
git push origin main
git tag -a v0.6.0 -m 'SocialPause 0.6.0'
git push origin v0.6.0
gh run list --workflow ci.yml --branch v0.6.0
```

Use `gh run watch RUN_ID --exit-status` for the tag run shown above. After it passes, create a draft with only the four release assets:

```sh
gh release create v0.6.0 --verify-tag --draft \
  --repo kroos47/SocialPause \
  --title 'SocialPause 0.6.0 — V2.1 design and flexible timers' \
  --notes-file .github/release-notes/v0.6.0.md \
  artifacts/releases/v0.6.0/SocialPause.apk \
  artifacts/releases/v0.6.0/SocialPause-source.zip \
  artifacts/releases/v0.6.0/BUILD-INFO.txt \
  artifacts/releases/v0.6.0/SHA256SUMS.txt
```

Review the draft's tag and assets, then publish:

```sh
gh release edit v0.6.0 --repo kroos47/SocialPause --draft=false --latest
```

If a tag/release already exists, inspect it before proceeding. Do not force-update a published tag or overwrite released assets silently. For later versions substitute the new version consistently. GitHub provides its own source snapshots too; the attached ZIP and build information make the APK's exact source commit explicit.
