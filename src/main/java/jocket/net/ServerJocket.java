package jocket.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import jocket.futex.Futex;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class ServerJocket implements Closeable {

  private static final int DEFAULT_MAX_PACKETS = Integer
      .getInteger("jocket.maxPackets", 1024);

  private static final int DEFAULT_CAPACITY = Integer
      .getInteger("jocket.capacity", 4 * 1024 * 1024);

  /**
   * Exchanged during the handshake. Changed whenever the layout or the
   * protocol of exchange files changes, so that incompatible versions do not
   * connect.
   */
  static final int MAGIC = 0x50C4E8;

  private final ServerSocket srv;

  private final int maxPackets;

  private final int capacity;

  private volatile boolean closed;

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

    srv.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
  }

  @Override
  public void close() throws IOException {
    closed = true;
    srv.close();
  }

  /**
   * Waits for a client to connect and complete the handshake. Clients which do
   * not complete it are ignored.
   *
   * @throws IOException if the server is closed, or cannot create exchange
   *         files
   */
  public JocketSocket accept() throws IOException {
    while (true) {

      if (closed)
        throw new IllegalStateException("Closed");

      try (Socket s = srv.accept()) {
        JocketSocket js = handshake(s);
        if (js != null)
          return js;
      }
    }
  }

  /**
   * Sends the paths of new exchange files to the client, which opens them.
   * Returns null if the client does not complete the handshake: it must not
   * prevent other clients from connecting (e.g. a port scanner, or a client
   * killed during the handshake).
   *
   * @throws IOException if exchange files cannot be created
   */
  private JocketSocket handshake(Socket s) throws IOException {
    DataInputStream in;
    DataOutputStream out;
    try {
      // allows to wakeup if client never does the handshake
      s.setSoTimeout(1000);
      in = new DataInputStream(s.getInputStream());
      out = new DataOutputStream(s.getOutputStream());
      out.writeInt(MAGIC);
      out.flush();
      if (in.readInt() != MAGIC)
        return null;
    }
    catch (IOException e) {
      return null;
    }

    // TODO: write parameters in file header (+ misc meta data)
    JocketFile fw = new JocketFile(maxPackets, capacity);
    JocketFile fr;
    try {
      fr = new JocketFile(maxPackets, capacity);
    }
    catch (IOException | RuntimeException e) {
      fw.close();
      throw e;
    }

    try {
      out.writeUTF(fw.getPath());
      out.writeUTF(fr.getPath());
      out.flush();

      // the client has opened both files
      in.readInt();

      JocketReader reader = fr.reader();
      JocketWriter writer = fw.writer();
      if (Futex.isAvailable()) {
        writer.useFutex();
        reader.useFutex();
      }
      return new JocketSocket(reader, writer);
    }
    catch (IOException e) {
      return null;
    }
    finally {
      // the client has opened the files, or never will. The mappings are
      // released (and the files deleted) when the socket is closed
      fw.deleteFile();
      fw.close();
      fr.deleteFile();
      fr.close();
    }
  }

  public int getLocalPort() {
    return srv.getLocalPort();
  }
}
