#!/bin/bash

# classpath is different when built with ant or gradle
for i in classes build/classes/java/main build/classes/java/test *.jar build/libs/*.jar
do
  CLASSPATH="$CLASSPATH:$i"
done

export CLASSPATH

java "$@" jocket.bench.BenchServer &
java "$@" jocket.bench.BenchClient
