#!/bin/bash

for i in classes *.jar
do
  CLASSPATH="$CLASSPATH:$i"
done

export CLASSPATH

java "$@" jocket.bench.BenchServer &
java "$@" jocket.bench.BenchClient
