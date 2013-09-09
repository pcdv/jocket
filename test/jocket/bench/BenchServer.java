package jocket.bench;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
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
public final class BenchServer {

  static final boolean USE_JOCKET = !Boolean.getBoolean("tcp");

  static final int PORT = Integer.getInteger("port", 3333);

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
    run(s.getInputStream(), s.getOutputStream());
  }

  private void initWithSocket() throws IOException {
    ServerSocket srv = new ServerSocket(PORT);
    System.out.println("Java ServerSocket listening on " + srv.getLocalPort());
    Socket s = srv.accept();
    srv.close();
    s.setTcpNoDelay(true);
    run(s.getInputStream(), new BufferedOutputStream(s.getOutputStream()));
  }

  private void run(InputStream in, OutputStream out) throws IOException {
    DataInputStream din = new DataInputStream(in);
    int reps = din.readInt();
    int replySize = din.readInt();
    byte[] buf = new byte[10 * 1024];
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
