#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# modelrack4j 0.1.0 refuses to build a registry with no connections, so an empty
# configuration file cannot start the app: it needs at least one block. --echo puts the
# test-scope fake provider on the classpath, which is what makes `provider = echo`
# resolvable and lets it start before any real key exists.
classes="target/classes"
if [[ "${1:-}" == "--echo" ]]; then
  shift
  mvn -q test-compile
  classes="target/classes:target/test-classes"
else
  mvn -q compile
fi

mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

exec java -cp "$classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main "$@"
