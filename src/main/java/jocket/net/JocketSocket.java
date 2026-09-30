package jocket.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jocket.futex.Futex;
import jocket.impl.JocketInputStream;
import jocket.impl.JocketOutputStream;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class JocketSocket implements Closeable {

  private static Thread hook;

  /**
   * Open sockets, closed by a shutdown hook.
   */
  private static final Set<JocketSocket> sockets = ConcurrentHashMap.newKeySet();

  private final JocketReader reader;

  private final JocketWriter writer;

  private final JocketOutputStream output;

  private final JocketInputStream input;

  JocketSocket(JocketReader reader, JocketWriter writer) {
    this.reader = reader;
    this.writer = writer;
    this.output = new JocketOutputStream(writer);
    this.input = new JocketInputStream(reader);
    addShutdownHook(this);
  }

  /**
   * Connects to specified port on local host (loopback interface) and returns
   * a pseudo-socket using shared memory to communicate.
   *
   * @param port
   * @throws IOException
   */
  public JocketSocket(int port) throws IOException {
    this(connect(port));
  }

  private JocketSocket(Ends ends) {
    this(ends.reader(), ends.writer());
  }

  /**
   * The reader and writer obtained from the handshake.
   */
  private record Ends(JocketReader reader, JocketWriter writer) {
  }

  /**
   * Performs the handshake: receives the paths of the exchange files from the
   * server, and opens them.
   */
  private static Ends connect(int port) throws IOException {
    try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
      // allows to wakeup if server never does the handshake
      s.setSoTimeout(5000);

      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeInt(ServerJocket.MAGIC);
      out.flush();

      DataInputStream in = new DataInputStream(s.getInputStream());
      int magic;
      try {
        magic = in.readInt();
      }
      catch (SocketTimeoutException | EOFException e) {
        magic = 0;
      }
      if (magic != ServerJocket.MAGIC)
        throw new IOException("Server does not support Jocket protocol");

      JocketFile jfr = JocketFile.openReceived(in.readUTF());
      JocketFile jfw = null;
      try {
        jfw = JocketFile.openReceived(in.readUTF());

        // tells the server that both files are open
        out.writeInt(0);
        out.flush();

        JocketReader reader = jfr.reader();
        JocketWriter writer = jfw.writer();
        if (Futex.isAvailable()) {
          writer.useFutex();
          reader.useFutex();
        }
        return new Ends(reader, writer);
      }
      finally {
        // the mappings are released when the socket is closed
        jfr.deleteFile();
        jfr.close();
        if (jfw != null) {
          jfw.deleteFile();
          jfw.close();
        }
      }
    }
  }

  private synchronized static void addShutdownHook(JocketSocket s) {

    if (hook == null) {
      hook = new Thread("Jocket-shutdown") {
        @Override
        public void run() {
          for (JocketSocket s : new ArrayList<>(sockets)) {
            s.close();
          }
        }
      };
      Runtime.getRuntime().addShutdownHook(hook);
    }
    sockets.add(s);
  }

  public JocketReader getReader() {
    return reader;
  }

  public JocketWriter getWriter() {
    return writer;
  }

  public OutputStream getOutputStream() {
    return output;
  }

  public InputStream getInputStream() {
    return input;
  }

  /**
   * Closes the socket. Can be called from any thread: waits for the read or
   * write in progress, if any.
   */
  @Override
  public void close() {
    reader.close();
    writer.close();
    sockets.remove(this);
  }

}
