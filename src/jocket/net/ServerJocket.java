package jocket.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerJocket implements Closeable {

  static final int MAGIC = 0x50C4E7;
  private final ServerSocket srv;
  private boolean closed;

  public ServerJocket(int port) throws IOException {
    srv = new ServerSocket();
    srv.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
  }

  @Override
  public void close() throws IOException {
    closed = true;
    srv.close();
  }

  public JocketSocket accept() throws IOException {
    while (true) {

      if (closed)
        throw new IllegalStateException("Closed");

      Socket s = srv.accept();
      DataInputStream in = new DataInputStream(s.getInputStream());
      int magic = in.readInt();

      if (magic != MAGIC) {
        s.close();
        continue;
      }

      // TODO: make parameters configurable through ServerJocket
      // TODO: write parameters in file header (+ misc meta data)
      JocketFile fw = new JocketFile();
      JocketFile fr = new JocketFile();

      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeUTF(fw.getPath());
      out.writeUTF(fr.getPath());

      in.readInt();
      fw.deleteFile();
      fr.deleteFile();
      s.close();

      return new JocketSocket(fr.reader(), fw.writer());
    }
  }

  public int getLocalPort() {
    return srv.getLocalPort();
  }
}
