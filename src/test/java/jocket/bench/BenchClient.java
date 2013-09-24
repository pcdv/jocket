package jocket.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

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

    doRun("Warming up", WARMUP, PAUSE > 0 ? 1 : 0);
    doRun("Running test", REPS, PAUSE);

    if (!NOSTATS) {
      System.out.printf("Dumping results in /tmp/%s\n", OUTPUT_FILE);
      dumpResults(OUTPUT_FILE);
    }
  }

  private long doRun(String msg, int reps, long pauseNanos) throws IOException {
    System.out.printf("%-15s: %10d reps, pause between reps: %dns...",
                      msg,
                      reps,
                      pauseNanos);
    long time = System.currentTimeMillis();
    for (int i = 0; i < reps; i++) {

      long nanos = System.nanoTime();
      iter(BATCH);
      nanos = (System.nanoTime() - nanos) / BATCH;

      if (i < REPS)
        this.nanos[i] = nanos;

      if (pauseNanos > 0)
        LockSupport.parkNanos(pauseNanos);
    }
    time = System.currentTimeMillis() - time;

    System.out.printf(" done in %dms\n", time);
    return time;
  }

  public void iter(int batch) throws IOException {
    for (int i = 0; i < batch; i++) {
      out.write(buf, 0, 4);
      out.flush();
      in.readFully(buf, 0, REPLY_SIZE);
    }
  }

  private static final double[] PTILES = { 1, 10, 50, 99, 99.9, 99.99, 99.999,
    99.9999 };

  private void dumpResults(String fileName) throws IOException {
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);

    PrintWriter w = new PrintWriter(new FileOutputStream("/tmp/" + fileName));
    for (int i = 0; i < nanos.length; i++)
      w.println((nanos[i] / 1000.0) + "\t" + (sorted[i] / 1000.0));
    w.close();

    for (double pc : PTILES) {
      logPctile(pc, sorted, 0.001, "us");
    }
  }

  private void logPctile(double pc, long[] sorted, double factor, String unit) {
    int index = (int) (pc / 100 * sorted.length);
    System.out.printf("%-12s  (%7d) : %8.2f (%s)\n",
                      pc + "%",
                      sorted.length - index,
                      sorted[index] * factor,
                      unit);
  }

  public static void main(String[] args) throws IOException {
    new BenchClient().bench();
  }
}
