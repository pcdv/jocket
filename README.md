Jocket
======

Low-latency replacement for local Java sockets, using shared memory.

It has been developed under Linux and works optimally on this platform
thanks to the use of a [futex](https://en.wikipedia.org/wiki/Futex) accessed
through JNI. The futex allows to implement inter-process wait/notify.

Jocket can work without a futex but it involves active waiting or sleeping
so it is not ideal in all situations. It is possible that a similar feature
exists on macOS/Windows but I have not looked into it yet.

I don't currently use Jocket in production but several people have
contacted me for advice or bug reports so I suspect some people do :)

Performance
-----------

Current benchmarks show a 10-100x performance improvement when compared to
a standard socket.

![alt text](docs/bench.png "The thick red line is around 500 nanoseconds")

The chart above displays the rountrip latency (in microseconds) between two
processes:
 - T0: the client sends a PING (4 bytes) to the server
 - T1: the client receives a PONG (1024 bytes) from the server

This benchmark was run on an old Dell D830 laptop with an Intel Core i5-2500
(4-core 3.30GHz) CPU.

The output of the benchmark should look like:

```
$ ./run-bench.sh -Dreps=500000
Jocket listening on 3333
Warming up     :      50000 reps, pause between reps: 0ns... done in 114ms
Running test   :     500000 reps, pause between reps: 0ns... done in 279ms
Dumping results in /tmp/Jocket
1.0%          ( 495000) :     0,50 (us)
10.0%         ( 450000) :     0,52 (us)
50.0%         ( 250000) :     0,53 (us)
99.0%         (   5000) :     0,57 (us)
99.9%         (    500) :     0,60 (us)
99.99%        (     51) :     1,97 (us)
99.999%       (      6) :     5,43 (us)
99.9999%      (      1) :    15,57 (us)

$ ./run-bench.sh -Dreps=500000 -Dtcp=true
Java ServerSocket listening on 3333
Warming up     :      50000 reps, pause between reps: 0ns... done in 737ms
Running test   :     500000 reps, pause between reps: 0ns... done in 9597ms
Dumping results in /tmp/Socket
1.0%          ( 495000) :     9,42 (us)
10.0%         ( 450000) :     9,79 (us)
50.0%         ( 250000) :    20,62 (us)
99.0%         (   5000) :    22,05 (us)
99.9%         (    500) :    32,69 (us)
99.99%        (     51) :   509,07 (us)
99.999%       (      6) :  2128,08 (us)
99.9999%      (      1) :  4546,98 (us)
```

The following options can be passed to the run-bench.sh script
 - -Dtcp=true : uses a normal socket instead of Jocket (default=false)
 - -Dreps=1000 : sets the number of repetitions to 1000 (default=300000)
 - -Dpause=1000 : pauses for 1000 nanoseconds between each rep (default=0)
 - -DreplySize=4096 : sets the size of the PONG reply (default=1024)
 - -Dwarmup=0 : sets the number of reps during warmup to 0 (default=50000)
 - -Dport=1234 : use port 1234 (default=3333)
 - -Dnostats=true : do not record and dump latencies (useful if number of reps is too big)

The client outputs a file /tmp/Jocket or /tmp/Socket which can be fed into Gnuplot, Excel etc. 

__Example with gnuplot:__

```
plot '/tmp/Jocket' using 1 with lines title 'Jocket', '/tmp/Socket' using 1 with lines title 'TCP (loopback)'
```

How to build
------------

Just run the following commands to build Jocket and run the benchmark.

`$JAVA_HOME` must be set to a valid JDK directory otherwise the JNI
build will fail.

```
git clone https://github.com/pcdv/jocket.git
cd jocket
./gradlew jni build testClasses
./run-bench.sh
./run-bench.sh -Dtcp=true
```

You can run `ant` instead of `gradlew` if you have it installed (although I
might remove ant support in the future).

Notes:
 - the JNI build requires Linux for its futex implementation
 - the build should now work on macOS and on Windows as the JNI library is
 not generated but the lack of inter process synchronization will make it
 suboptimal

Using Jocket as a dependency
----------

Jocket is now published on [bintray/jcenter](https://bintray.com/paulcdv/maven/jocket)
so you can add the following to your `build.gradle` file:

```groovy
repositories {
  jcenter()
}

dependencies {
  compile 'com.github.pcdv.jocket:jocket:0.2'
}
```

NB: the published jar includes the linux x64 JNI library.

API
---

Currently, there are 2 APIs:
 - JocketReader/JocketWriter: low-level, non blocking API
 - JocketSocket: high-level, blocking API mimicking java.net.Socket

The benchmark used to generate the above diagram is based on the JocketSocket API.


How it works
------------

Jocket is built around a concept of shared buffer, accessed by a reader and a writer.

`JocketWriter.write()` can be called several times to append data to the buffer. When `JocketWriter.flush()` is called, a packet is flushed and can immediately be read by `JocketReader.read()`.

The buffer is bound by a double capacity:
 - the maximum number of packets that can be written without being read
 - the maximum number of bytes that can be written without being read

When any of these limits is reached, `JocketWriter.write()` does nothing and returns zero. As soon as `JocketReader.read()` is called, it is again possible to write data.

The reader and writer can be:
 - in the same process: allows to transfer efficiently a stream of bytes from a thread to another
 - in two different processes: allows to transmit data between two local processes faster than with a TCP socket

To implement a bidirectional socket, two shared buffers are required, each wrapping an mmap'ed file.

For convenience, the current jocket API closely mimics the java.net API, eg.


```java
// server
ServerJocket srv = new ServerJocket(4242);
JocketSocket sock = srv.accept();

// client
JocketSocket sock = new JocketSocket(4242);
InputStream in = sock.getInputStream();
OutputStream out = sock.getOutputStream();
```

Otherwise, Jocket readers and writers have their own API allowing to perform non-blocking read/writes, 
potentially faster than with input/output streams.
