#!/bin/bash
# Runs the benchmark. Build first with: ./gradlew testClasses

export CLASSPATH=build/classes/java/main:build/resources/main:build/classes/java/test

java "$@" jocket.bench.BenchServer &
server=$!
# the server must not outlive the client, which may fail
trap 'kill $server 2>/dev/null' EXIT

java "$@" jocket.bench.BenchClient
