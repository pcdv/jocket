Jocket
======

Low-latency replacement for local Java sockets, using shared memory.

It has been developed under Linux and works optimally on this platform
thanks to the use of a [futex](https://en.wikipedia.org/wiki/Futex) accessed
through JNI. The futex allows to implement inter-process wait/notify.

Jocket also works on macOS and Windows, without a futex: a thread waiting for
data spins, then yields, then sleeps for increasing durations. This keeps the
latency low while data flows, but is not ideal in all situations. It is
possible that a similar feature exists on macOS/Windows but I have not looked
into it yet.

Jocket requires Java 21.

I don't currently use Jocket in production but several people have
contacted me for advice or bug reports so I suspect some people do :)

Performance
-----------

Current benchmarks show a 10-100x performance improvement when compared to
a standard socket.

![alt text](docs/bench.png "The thick red line is around 500 nanoseconds")

The chart above displays the roundtrip latency (in microseconds) between two
processes:
 - T0: the client sends a PING (4 bytes) to the server
 - T1: the client receives a PONG (1024 bytes) from the server

This benchmark was run in 2017 on an Intel Core i5-2500 (4-core 3.30GHz) CPU.
In 2026, the median roundtrip is still around 0.5 microseconds (under WSL2,
on an Intel Core Ultra 7 155H laptop).

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

The client outputs a file Jocket or Socket in the temporary directory (/tmp
under Linux), which can be fed into Gnuplot, Excel etc.

__Example with gnuplot:__

```
plot '/tmp/Jocket' using 1 with lines title 'Jocket', '/tmp/Socket' using 1 with lines title 'TCP (loopback)'
```

How to build
------------

Gradle uses a locally installed JDK 21, or downloads one if none is found.

Just run the following commands to build Jocket and run the benchmark.

```
git clone https://github.com/pcdv/jocket.git
cd jocket
./gradlew build
./run-bench.sh
./run-bench.sh -Dtcp=true
```

Under Linux, the build also compiles the JNI futex library, which requires
`gcc`. The library is only built for the architecture of the build machine.

Using Jocket as a dependency
----------------------------

Jocket is available through [JitPack](https://jitpack.io/#pcdv/jocket), so
you can add the following to your `build.gradle` file:

```groovy
repositories {
  maven { url 'https://jitpack.io' }
}

dependencies {
  implementation 'com.github.pcdv:jocket:<version>'
}
```

NB: the jar includes the JNI library for Linux on x86-64, the architecture on
which JitPack builds it. Elsewhere (e.g. Linux on ARM), Jocket works without
the futex.

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

The client and the server perform a handshake through a TCP socket bound to
the loopback interface: the server sends the paths of the two exchange files,
which the client opens. Both sides then delete the files, which remain mapped.

Closing and failures
--------------------

 - `close()` can be called from any thread, e.g. to wake up a thread blocked
   reading: it waits for the read or write in progress before unmapping the
   exchange file. A shutdown hook closes the sockets left open.
 - When the writer closes, the reader first reads all the data flushed
   before, then gets the end of stream (-1).
 - Each side checks that the other process is alive when it cannot progress
   (no data to read, or no space to write), at most once per second. If the
   peer died without closing anything (e.g. killed with SIGKILL), a reader
   gets the end of stream, and a writer gets a `ClosedException`. This check
   is skipped when the two processes do not share the same PID namespace
   (e.g. two containers sharing `/dev/shm`).

Security
--------

 - Exchange files are created with a random name, readable and writable by
   their owner only: the client must run as the same user as the server.
 - The client only opens regular files of the exchange directory, named like
   the ones servers create, and without following symbolic links.
 - Like a TCP server on the loopback interface, a ServerJocket accepts
   connections from any local process.

Configuration
-------------

The following system properties can be set:
 - `jocket.dir`: directory of the exchange files. Defaults to `/dev/shm`
   (tmpfs, i.e. memory only) under Linux, and to the temporary directory
   elsewhere. It must be the same for the server and its clients.
 - `jocket.maxPackets` and `jocket.capacity`: default number of packets and
   default data capacity (in bytes) of the exchange files created by a
   ServerJocket. Both must be powers of 2. Default to 1024 and 4194304 (4MB).
 - `jocket.peerCheckMillis`: minimum time between two checks that the peer
   process is alive. Defaults to 1000.

Changes in 0.3 (unreleased)
---------------------------

 - Requires Java 21.
 - The layout of exchange files and the handshake have changed: Jocket 0.3
   cannot connect to earlier versions.
 - Memory accesses have the ordering guarantees they need, including on ARM
   (e.g. Apple Silicon), through VarHandles.
 - Fixed crashes when a socket is used or closed concurrently, or used after
   being closed, and data loss or corruption in several corner cases.
 - Detection of dead peers, progressive backoff when waiting without a futex.
 - Hardened handshake: robust `accept()`, private exchange files, checked
   paths.
