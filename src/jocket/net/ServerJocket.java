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

import jocket.futex.Futex;

public class ServerJocket implements Closeable {

  private static final int DEFAULT_MAX_PACKETS = Integer
      .getInteger("jocket.maxPackets", 1024);

  private static final int DEFAULT_CAPACITY = Integer
      .getInteger("jocket.capacity", 4 * 1024 * 1024);

  static final int MAGIC = 0x50C4E7;

  private final ServerSocket srv;

  private final int maxPackets;

  private final int capacity;

  private boolean closed;

  /**
   * Creates a new Jocket server with default settings.
   *
   * @param port the TCP port on which Jocket will listen for connections
   * @throws IOException
   */
  public ServerJocket(int port) throws IOException {
    this(port, DEFAULT_MAX_PACKETS, DEFAULT_CAPACITY);
  }

  public ServerJocket(int port, int maxPackets, int capacity) throws IOException {
    if (Integer.bitCount(maxPackets) != 1)
      throw new IllegalArgumentException("Max packets must be a power of 2");

    if (Integer.bitCount(capacity) != 1)
      throw new IllegalArgumentException("Capacity must be a power of 2");

    this.maxPackets = maxPackets;
    this.capacity = capacity;
    srv = new ServerSocket();

    // FIXME: getLoopackAddress() == @since 1.7
    srv.bind(new InetSocketAddress(InetAddress.getByName(null), port));
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
        continue;
      }

      // TODO: make parameters configurable through ServerJocket
      // TODO: write parameters in file header (+ misc meta data)
      JocketFile fw = new JocketFile(maxPackets, capacity);
      JocketFile fr = new JocketFile(maxPackets, capacity);

      if (Futex.isAvailable()) {
        fw.writer().useFutex();
        fr.reader().useFutex();
      }

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
