#!/bin/bash
# Runs the benchmark. Build first with: ./gradlew testClasses

export CLASSPATH=build/classes/java/main:build/resources/main:build/classes/java/test

java "$@" jocket.bench.BenchServer &
java "$@" jocket.bench.BenchClient
