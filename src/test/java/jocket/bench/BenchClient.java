package jocket.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.locks.LockSupport;

import jocket.futex.Futex;
import jocket.net.JocketSocket;

/**
 * Client (and master) part of the client/server benchmark.
 *
 * @author pcdv
 */
public final class BenchClient implements Settings {

  private final byte[] buf;

  private final long[] nanos;

  private DataInputStream in;

  private DataOutputStream out;

  public BenchClient() throws IOException {
    this.nanos = new long[REPS];
    this.buf = new byte[REPLY_SIZE];
    if (USE_JOCKET) {
      JocketSocket s = new JocketSocket(PORT);
      in = new DataInputStream(s.getInputStream());
      out = new DataOutputStream(s.getOutputStream());
    }
    else {
      @SuppressWarnings("resource")
      Socket s = new Socket("localhost", PORT);
      s.setTcpNoDelay(true);
      in = new DataInputStream(s.getInputStream());
      out = new DataOutputStream(s.getOutputStream());
    }
  }

  /**
   * Benchmark's main loop.
   */
  public void bench() throws IOException {

    out.writeInt(REPS * BATCH + WARMUP);
    out.writeInt(REPLY_SIZE);

    System.out.println("Warmup");
    doRun(WARMUP);
    System.out.println("Go");
    long time = doRun(REPS);

    System.out.printf("Done in %dms. Dumping results in %s\n",
                      time,
                      OUTPUT_FILE);

    dumpResults(OUTPUT_FILE);
  }

  private long doRun(int reps) throws IOException {
    long time = System.currentTimeMillis();
    System.out.println("Starting " + reps + " iterations");
    for (int i = 0; i < reps; i++) {

      long nanos = Futex.rdtsc();
      iter(BATCH);
      nanos = (Futex.rdtsc() - nanos) / BATCH;

      if (i < REPS)
        this.nanos[i] = nanos;

      if (PAUSE > 0)
        LockSupport.parkNanos(PAUSE);
    }
    time = System.currentTimeMillis() - time;

    return time;
  }

  public void iter(int batch) throws IOException {
    for (int i = 0; i < batch; i++) {
      out.write(buf, 0, 4);
      out.flush();
      in.readFully(buf, 0, REPLY_SIZE);
    }
  }

  private void dumpResults(String fileName) throws IOException {
    PrintWriter w = new PrintWriter(new FileOutputStream("/tmp/" + fileName));
    for (int i = 0; i < nanos.length; i++)
      w.println(nanos[i] / 1000.0);
    w.close();
  }

  public static void main(String[] args) throws IOException {
    new BenchClient().bench();
  }
}
