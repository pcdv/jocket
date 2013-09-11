package jocket.bench;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

/**
 * Server part of the client/server benchmark.
 *
 * @author pcdv
 */
public final class BenchServer implements Settings {

  public BenchServer() throws IOException {
    if (USE_JOCKET)
      initWithJocket();
    else
      initWithSocket();
  }

  private void initWithJocket() throws IOException {
    ServerJocket srv = new ServerJocket(PORT);
    System.out.println("Jocket listening on " + srv.getLocalPort());
    JocketSocket s = srv.accept();
    srv.close();
    run(new DataInputStream(s.getInputStream()), s.getOutputStream());
  }

  private void initWithSocket() throws IOException {
    ServerSocket srv = new ServerSocket(PORT);
    System.out.println("Java ServerSocket listening on " + srv.getLocalPort());
    Socket s = srv.accept();
    srv.close();
    s.setTcpNoDelay(true);
    run(new DataInputStream(s.getInputStream()), s.getOutputStream());
  }

  private void run(final DataInputStream din, final OutputStream out)
      throws IOException {
    final int reps = din.readInt();
    final int replySize = din.readInt();
    final byte[] buf = new byte[Math.max(4, replySize)];
    for (int i = 0; i < reps; i++) {
      din.readFully(buf, 0, 4);
      out.write(buf, 0, replySize);
      out.flush();
    }
  }

  public static void main(String[] args) throws IOException {
    new BenchServer();
  }
}
