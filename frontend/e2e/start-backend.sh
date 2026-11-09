#!/usr/bin/env sh
# Starts the built backend for the end-to-end tests (Playwright runs this; see playwright.config.ts).
# Build the jar first:  cd backend && ./mvnw -DskipTests package
# The `local` profile reads the database settings from infra/.env, or from POSTGRES_* variables.
set -eu
cd "$(dirname "$0")/../../backend"

JAR=$(ls target/agentshelf-backend-*.jar 2>/dev/null | grep -v '\.original$' | head -n 1 || true)
if [ -z "$JAR" ]; then
  echo "Backend jar not found. Build it first: cd backend && ./mvnw -DskipTests package" >&2
  exit 1
fi

# Prefer a Java 25 found by macOS, even if JAVA_HOME points to an older JDK. CI sets JAVA_HOME.
if [ -x /usr/libexec/java_home ] && JDK25=$(/usr/libexec/java_home -v 25 2>/dev/null); then
  JAVA="$JDK25/bin/java"
else
  JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
fi

VERSION=$("$JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')
if [ "${VERSION%%.*}" -lt 25 ] 2>/dev/null; then
  echo "The backend needs Java 25 or newer, found $VERSION at $JAVA." >&2
  exit 1
fi

exec "$JAVA" -jar "$JAR" --spring.profiles.active=local
