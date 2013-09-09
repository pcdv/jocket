package jocket.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import jocket.net.JocketSocket;

/**
 * A standalone benchmark running the both client and server.
 *
 * @author pcdv
 */
@SuppressWarnings("resource")
public final class BenchClient {

  private final int reps = Integer.getInteger("reps", 300000);

  private final int replySize = Integer.getInteger("replySize", 1024);

  private final int batch = Integer.getInteger("batch", 1);

  private final boolean useJocket = !Boolean.getBoolean("tcp");

  private final int port = Integer.getInteger("port", 3333);

  private final int warmup = Integer.getInteger("warmup", 50000);

  private final byte[] buf;

  private final long[] nanos;

  private final String fileName;

  private DataInputStream in;

  private DataOutputStream out;

  public BenchClient() throws IOException {
    this.nanos = new long[reps];
    this.buf = new byte[replySize];
    if (useJocket)
      this.fileName = initWithJocket();
    else
      this.fileName = initWithSocket();
  }

  private String initWithJocket() throws IOException {
    JocketSocket s = new JocketSocket(port);
    in = new DataInputStream(s.getInputStream());
    out = new DataOutputStream(s.getOutputStream());
    return "/tmp/Jocket";
  }

  private String initWithSocket() throws IOException {
    Socket s = new Socket("localhost", port);
    s.setTcpNoDelay(true);
    in = new DataInputStream(s.getInputStream());
    out = new DataOutputStream(s.getOutputStream());
    return "/tmp/Socket";
  }

  /**
   * Benchmark's main loop.
   */
  public void bench() throws IOException {

    long time = System.currentTimeMillis();
    out.writeInt(reps * batch + warmup);
    out.writeInt(replySize);

    System.out.println("Warmup");
    for (int i = 0; i < warmup; i++) {
      iter(1);
    }

    System.out.println("Starting " + reps + " iterations");
    for (int i = 0; i < reps; i++) {
      long start = System.nanoTime();
      iter(batch);
      nanos[i] = (System.nanoTime() - start) / batch;
    }
    time = System.currentTimeMillis() - time;

    System.out.printf("Done in %dms. Dumping results in %s\n", time, fileName);
    dumpResults(fileName);
  }

  public void iter(int batch) throws IOException {
    for (int i = 0; i < batch; i++) {
      // send request
      out.write(buf, 0, 4);
      out.flush();

      // read response
      in.readFully(buf, 0, replySize);
    }
  }

  private void dumpResults(String fileName) throws IOException {
    PrintWriter w = new PrintWriter(new FileOutputStream(fileName));
    for (int i = 0; i < nanos.length; i++)
      w.println(nanos[i] / 1000.0);
    w.close();
  }

  public static void main(String[] args) throws IOException {
    new BenchClient().bench();
  }
}
