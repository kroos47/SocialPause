#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Keep caches and debug-only signing files inside this project.
PROJECT_DIR=$(pwd)
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PROJECT_DIR/.tools/gradle-cache}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PROJECT_DIR/.tools/android-user}"
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in "$PROJECT_DIR"/.tools/jdk21/*/Contents/Home; do
        if [ -x "$candidate/bin/jlink" ]; then export JAVA_HOME="$candidate"; break; fi
    done
    if [ -z "${JAVA_HOME:-}" ]; then
        echo 'Set JAVA_HOME to a complete JDK 17 or 21. See docs/ANDROID_STUDIO.md.' >&2
        exit 1
    fi
fi
exec sh ./gradlew --no-daemon :engine:check :app:assembleDebug :app:lintDebug --console=plain "$@"
