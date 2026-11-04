#!/usr/bin/env bash
# Run the full local verification: the 123-check audit suite plus the Gradle unit tests
# when an Android SDK/JDK is available (CI does this on every push).
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d node_modules ]; then
    echo "Installing dependencies (npm ci)..."
    npm ci
fi

npm test

if command -v ./android/gradlew >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
    echo
    echo "Running Android unit tests..."
    (cd android && ./gradlew test --console=plain)
else
    echo
    echo "Skipping Gradle unit tests: no JDK (or gradlew) on this machine."
    echo "CI runs them on every push: .github/workflows/build.yml"
fi
