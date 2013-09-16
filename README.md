Jocket
======

Low-latency replacement for local Java sockets, using shared memory.


Status
------

Current benchmarks show a 10-100x performance improvement when compared to a standard socket.

The following chart displays the RTT latency (in microseconds) between two processes:
 - the client sends a PING request (payload = 4 bytes)
 - the server replies with a PONG response (payload = 1024 bytes)

![alt text](docs/bench.png "Latency for an 1kb PING. Red = Socket, green = Jocket. The thick green line is roughly between 0.50 and 0.56 microseconds")

This benchmark was run on an Intel Core i5-2500.

Jocket is young and still under development. It has been tested only on Linux.

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


Running the benchmarks
----------------------

Server:
```
java -cp jocket-0.4.0.jar jocket.bench.BenchServer
```

Client:
```
java -cp jocket-0.4.0.jar jocket.bench.BenchClient
```

The following system properties are available in the client:
 - -Dtcp=true : uses a normal socket instead of Jocket (default=false) [NB: must also be set on server side]
 - -Dreps=1000 : sets the number of repetitions to 1000 (default=300000)
 - -Dpause=1000 : pauses for 1000 nanoseconds between each rep (default=0)
 - -DreplySize=4096 : sets the size of the PONG reply (default=1024)
 - -Dwarmup=0 : sets the number of reps during warmup to 0 (default=50000)
 - -Dport=1234 : use port 1234 (default=3333)

The client outputs a file /tmp/Jocket or /tmp/Socket which can be fed into Gnuplot, Excel etc.

Credits
-------

This project takes some ideas from @mjpt777 and @peter-lawrey


Changes
-------

### 0.4.0

Includes a new waiting strategy based on a [Futex](http://en.wikipedia.org/wiki/Futex). This avoids too heavy spinning and
keeps latency low even no data has been received for a long time.

### 0.3.0

Align packets on cache lines by default (avoids false sharing).

Use native ordering in direct byte buffer to avoid useless byte swaps.
