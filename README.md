Jocket
======

Faster java.net.Socket alternative using shared memory.

Jocket is built around a concept of shared buffer, in which a JocketWriter pushes data and that a JocketReader can read.

The reader and writer can be:
 - in the same process: allows to transfer efficiently a stream of bytes from a thread to another
 - in two different processes: allows to transmit data between two local processes faster than with a TCP socket

To implement a bidirectional socket, two shared buffers are required, each wrapping an mmap'ed file.

For convenience, the jocket API closely mimics the java.net API, eg.


```java
// server
ServerJocket srv = new ServerJocket(4242);
JocketSocket sock = srv.accept();

// client
JocketSocket sock = new JocketSocket(4242);
InputStream in = sock.getInputStream();
OutputStream out = sock.getOutputStream();
```

Otherwise, Jocket readers and writers have their own API allowing to perform non-blocking read/writes, potentially faster than with input/output streams.


Status
------

Jocket is young and still in progress.

Current benchmarks show a 10-50x performance improvement when compared to a standard socket.

The following chart displays the RTT latency (in microseconds) between two processes:
 - the client sends a PING request (payload = 4 bytes)
 - the server replies with a PONG response (payload = 1024 bytes)

![alt text](docs/bench.png "Latency for an 1kb PING. Red = Socket, green = Jocket")


Credits
-------

This project takes some ideas from @mjpt777 and @peter-lawrey
