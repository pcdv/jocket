#!/bin/bash
# Runs the benchmark. Build first with: ./gradlew testClasses

sep=:
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) sep=';' ;; # Windows
esac
export CLASSPATH="build/classes/java/main${sep}build/resources/main${sep}build/classes/java/test"

java "$@" jocket.bench.BenchServer &
server=$!
# the server must not outlive the client, which may fail
trap 'kill $server 2>/dev/null' EXIT

java "$@" jocket.bench.BenchClient
