package jocket.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import jocket.net.JocketSocket;

/**
 * Infinite benchmark, meant for profiling.
 *
 * @author pcdv
 */
public final class BenchClient2 implements Settings {

  private final byte[] buf;

  private final DataInputStream in;

  private final DataOutputStream out;

  public BenchClient2() throws IOException {
    this.buf = new byte[REPLY_SIZE];
    JocketSocket s = new JocketSocket(PORT);
    in = new DataInputStream(s.getInputStream());
    out = new DataOutputStream(s.getOutputStream());
  }

  /**
   * Benchmark's main loop.
   */
  public void bench() throws IOException {

    out.writeInt(REPS * BATCH + WARMUP);
    out.writeInt(REPLY_SIZE);

    while (true) {
      iter(BATCH);
    }
  }

  public void iter(int batch) throws IOException {
    for (int i = 0; i < batch; i++) {
      out.write(buf, 0, 4);
      out.flush();
      in.readFully(buf, 0, REPLY_SIZE);
    }
  }

  public static void main(String[] args) throws IOException {
    new BenchClient2().bench();
  }
}
