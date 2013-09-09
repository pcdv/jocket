package jocket.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ServerJocket implements Closeable {

  static final int MAGIC = 0x50C4E7;

  private final ServerSocket srv;

  private boolean closed;

  public ServerJocket(int port) throws IOException {
    srv = new ServerSocket();

    // FIXME: getLoopackAddress() == @since 1.7
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
      // allows to wakeup if client never does the handshake
      s.setSoTimeout(1000);

      DataInputStream in = new DataInputStream(s.getInputStream());
      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeInt(MAGIC);
      out.flush();

      int magic = 0;
      try {
        magic = in.readInt();
      }
      catch (SocketTimeoutException timeout) {
      }

      if (magic != MAGIC) {
        s.close();
        //throw new IOException("Client does not support the Jocket protocol");
        continue;
      }

      // TODO: make parameters configurable through ServerJocket
      // TODO: write parameters in file header (+ misc meta data)
      JocketFile fw = new JocketFile();
      JocketFile fr = new JocketFile();

      out.writeUTF(fw.getPath());
      out.writeUTF(fr.getPath());
      out.flush();

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
