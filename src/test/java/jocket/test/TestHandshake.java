package jocket.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jocket.net.JocketFile;
import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of the handshake between JocketSocket and ServerJocket, which exchange
 * the paths of the exchange files through a TCP socket.
 */
public class TestHandshake {

  private ServerJocket srv;

  @TempDir
  Path tmp;

  @AfterEach
  public void tearDown() throws IOException {
    if (srv != null)
      srv.close();
  }

  /**
   * A client closing its connection at once (e.g. a port scanner) used to make
   * accept() throw an exception.
   */
  @Test
  public void testAcceptIgnoresClientClosingAtOnce() throws Exception {
    srv = new ServerJocket(0);
    FutureTask<JocketSocket> accept = startAccept();

    new Socket(InetAddress.getLoopbackAddress(), srv.getLocalPort()).close();

    checkConnection(accept);
  }

  /**
   * A client leaving before the end of the handshake used to make accept()
   * throw an exception, and the exchange files were not deleted.
   */
  @Test
  public void testAcceptIgnoresIncompleteHandshake() throws Exception {
    srv = new ServerJocket(0);
    FutureTask<JocketSocket> accept = startAccept();

    String path1, path2;
    try (Socket s = new Socket(InetAddress.getLoopbackAddress(), srv.getLocalPort())) {
      DataInputStream in = new DataInputStream(s.getInputStream());
      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeInt(in.readInt()); // magic
      path1 = in.readUTF();
      path2 = in.readUTF();
    }

    checkConnection(accept);
    assertFalse(Files.exists(Paths.get(path1)), path1);
    assertFalse(Files.exists(Paths.get(path2)), path2);
  }

  /**
   * A client used to open, write into and delete any path sent by the server.
   * Any local process listening on the port could make it destroy any file it
   * can access.
   */
  @Test
  public void testClientRejectsFileOutsideExchangeDirectory() throws Exception {
    Path victim = Files.writeString(tmp.resolve("victim"), "precious data, do not delete");
    checkClientRejects(victim);
    assertEquals("precious data, do not delete", Files.readString(victim));
  }

  /**
   * Same as above, with a symbolic link in the exchange directory.
   */
  @Test
  public void testClientRejectsSymbolicLink() throws Exception {
    Path victim = Files.writeString(tmp.resolve("victim"), "precious data, do not delete");
    Path link = exchangeDirectory().resolve("jocket-link-" + System.nanoTime());
    try {
      Files.createSymbolicLink(link, victim);
    }
    catch (IOException | UnsupportedOperationException e) {
      assumeTrue(false, "cannot create symbolic links: " + e);
    }
    try {
      checkClientRejects(link);
      assertEquals("precious data, do not delete", Files.readString(victim));
    }
    finally {
      Files.deleteIfExists(link);
    }
  }

  /**
   * Exchange files used to be created with the default permissions, usually
   * readable by all users.
   */
  @Test
  public void testExchangeFilesAreOnlyAccessibleToOwner() throws Exception {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    JocketFile jf = new JocketFile(16, 1024);
    try {
      Path path = Paths.get(jf.getPath());
      assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
    }
    finally {
      jf.close();
    }
  }

  /**
   * Runs a fake server which sends specified path as the path of both exchange
   * files, and checks that the client refuses to connect.
   */
  private static void checkClientRejects(Path path) throws Exception {
    try (ServerSocket fake = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      FutureTask<Void> server = new FutureTask<>(() -> {
        try (Socket s = fake.accept()) {
          DataInputStream in = new DataInputStream(s.getInputStream());
          DataOutputStream out = new DataOutputStream(s.getOutputStream());
          out.writeInt(in.readInt()); // magic
          out.writeUTF(path.toString());
          out.writeUTF(path.toString());
          out.flush();
          in.read(); // waits for the client to leave
        }
        return null;
      });
      Thread t = new Thread(server, "fake-server");
      t.setDaemon(true);
      t.start();

      assertThrows(IOException.class, () -> new JocketSocket(fake.getLocalPort()));
    }
  }

  /**
   * Returns the directory in which exchange files are created.
   */
  private static Path exchangeDirectory() throws IOException {
    JocketFile jf = new JocketFile(16, 1024);
    try {
      return Paths.get(jf.getPath()).getParent();
    }
    finally {
      jf.close();
      jf.deleteFile();
    }
  }

  private FutureTask<JocketSocket> startAccept() {
    FutureTask<JocketSocket> accept = new FutureTask<>(srv::accept);
    Thread t = new Thread(accept, "accept");
    t.setDaemon(true);
    t.start();
    return accept;
  }

  /**
   * Connects a real client and checks that accept() returns the other end.
   */
  private void checkConnection(FutureTask<JocketSocket> accept) throws Exception {
    try (JocketSocket c = new JocketSocket(srv.getLocalPort())) {
      JocketSocket s = accept.get(5, TimeUnit.SECONDS);
      c.getOutputStream().write(42);
      c.getOutputStream().flush();
      assertEquals(42, s.getInputStream().read());
      s.close();
    }
  }
}
