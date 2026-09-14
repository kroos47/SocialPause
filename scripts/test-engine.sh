#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p engine/build/standalone
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d engine/build/standalone engine/src/main/java/app/socialpause/engine/*.java engine/src/test/java/app/socialpause/engine/*.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp engine/build/standalone app.socialpause.engine.EngineTests
