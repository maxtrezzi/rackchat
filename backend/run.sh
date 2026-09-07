#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

force_echo=false
if [[ "${1:-}" == "--echo" ]]; then
  force_echo=true
  shift
fi

# The fake provider lives in the test sources, so `provider = echo` resolves only with
# target/test-classes on the classpath. Which configuration is about to be read decides
# whether that is needed - resolved here the way Main resolves it: first argument, then
# RACKCHAT_CONFIG, then rackchat.conf in this directory. --echo forces it on, for a
# configuration that has no echo block yet but is about to get one from the editor.
config="${1:-${RACKCHAT_CONFIG:-rackchat.conf}}"
if [[ "$force_echo" == true ]] \
    || grep -Eq 'provider[[:space:]]*=[[:space:]]*"?echo"?' "$config" 2>/dev/null; then
  mvn -q test-compile
  classes="target/classes:target/test-classes"
else
  mvn -q compile
  classes="target/classes"
fi

mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

exec java -cp "$classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main "$@"
