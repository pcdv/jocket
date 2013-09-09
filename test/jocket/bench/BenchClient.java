package jocket.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import jocket.net.JocketSocket;

/**
 * Client (and master) part of the client/server benchmark.
 *
 * @author pcdv
 */
public final class BenchClient {

  static final int REPS = Integer.getInteger("reps", 300000);

  static final int REPLY_SIZE = Integer.getInteger("replySize", 1024);

  static final int BATCH = Integer.getInteger("batch", 1);

  static final boolean USE_JOCKET = !Boolean.getBoolean("tcp");

  static final int PORT = Integer.getInteger("port", 3333);

  static final int WARMUP = Integer.getInteger("warmup", 50000);

  private final byte[] buf;

  private final long[] nanos;

  private final String fileName;

  private DataInputStream in;

  private DataOutputStream out;

  public BenchClient() throws IOException {
    this.nanos = new long[REPS];
    this.buf = new byte[REPLY_SIZE];
    if (USE_JOCKET)
      this.fileName = initWithJocket();
    else
      this.fileName = initWithSocket();
  }

  private String initWithJocket() throws IOException {
    JocketSocket s = new JocketSocket(PORT);
    in = new DataInputStream(s.getInputStream());
    out = new DataOutputStream(s.getOutputStream());
    return "/tmp/Jocket";
  }

  @SuppressWarnings("resource")
  private String initWithSocket() throws IOException {
    Socket s = new Socket("localhost", PORT);
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
    out.writeInt(REPS * BATCH + WARMUP);
    out.writeInt(REPLY_SIZE);

    System.out.println("Warmup");
    for (int i = 0; i < WARMUP; i++) {
      iter(1);
    }

    System.out.println("Starting " + REPS + " iterations");
    for (int i = 0; i < REPS; i++) {
      long start = System.nanoTime();
      iter(BATCH);
      nanos[i] = (System.nanoTime() - start) / BATCH;
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
      in.readFully(buf, 0, REPLY_SIZE);
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
