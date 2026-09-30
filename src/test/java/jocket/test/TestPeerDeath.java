package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jocket.impl.ClosedException;
import jocket.impl.Const;
import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A peer process dying without closing anything (e.g. killed with SIGKILL)
 * used to leave the other side waiting forever (issue #1).
 */
public class TestPeerDeath extends AbstractJocketTest {

  private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(10);

  private Process peer;

  private ServerJocket srv;

  @AfterEach
  public void tearDown() throws IOException {
    if (peer != null)
      peer.destroyForcibly();
    if (srv != null)
      srv.close();
  }

  @Test
  public void testReaderDetectsDeadWriter() throws Exception {
    init(4, 128);
    write("last words");
    shared.putLong(Const.WPID, deadPid());

    read("last words"); // written before dying
    long t0 = System.nanoTime();
    int res;
    while ((res = r.read(new byte[16])) == 0)
      assertTrue(System.nanoTime() - t0 < TIMEOUT, "death not detected");
    assertEquals(-1, res);
  }

  @Test
  public void testWriterDetectsDeadReader() throws Exception {
    init(4, 128);
    while (write("fill") > 0)
      ;
    shared.putLong(Const.RPID, deadPid());

    long t0 = System.nanoTime();
    ClosedException e = assertThrows(ClosedException.class, () -> {
      while (w.write(new byte[4], 0, 4) == 0)
        assertTrue(System.nanoTime() - t0 < TIMEOUT, "death not detected");
    });
    assertEquals("Reader is dead", e.getMessage());
  }

  /**
   * The pid of a process of another PID namespace (e.g. another container)
   * must not be checked: it designates another process, or none.
   */
  @Test
  public void testPeerOfOtherNamespaceIsNotChecked() throws Exception {
    init(4, 128);
    shared.putLong(Const.WPID + 8, shared.getLong(Const.WPID + 8) + 1);
    shared.putLong(Const.WPID, deadPid());

    // more than the interval between two checks
    long t0 = System.nanoTime();
    while (System.nanoTime() - t0 < TimeUnit.MILLISECONDS.toNanos(1500))
      assertEquals(0, r.read(new byte[16]));
    assertFalse(r.isClosed());
  }

  /**
   * A reader blocked on a socket (on the futex under Linux) whose peer is
   * killed.
   */
  @Test
  public void testBlockedReaderDetectsKilledPeer() throws Exception {
    JocketSocket s = connectPeer(ServerJocket::new);
    InputStream in = s.getInputStream();
    FutureTask<Integer> read = new FutureTask<>(() -> in.read());
    Thread reader = new Thread(read, "reader");
    reader.setDaemon(true);
    reader.start();

    peer.destroyForcibly().waitFor();
    assertEquals(-1, read.get(TIMEOUT, TimeUnit.NANOSECONDS));
  }

  /**
   * A writer blocked on a full socket buffer whose peer (which never reads) is
   * killed.
   */
  @Test
  public void testBlockedWriterDetectsKilledPeer() throws Exception {
    JocketSocket s = connectPeer(port -> new ServerJocket(port, 16, 4096));
    OutputStream out = s.getOutputStream();
    FutureTask<Void> write = new FutureTask<>(() -> {
      while (true)
        out.write(new byte[1024]);
    });
    Thread writer = new Thread(write, "writer");
    writer.setDaemon(true);
    writer.start();

    peer.destroyForcibly().waitFor();
    Throwable e = assertThrows(Exception.class, () -> write.get(TIMEOUT, TimeUnit.NANOSECONDS));
    assertInstanceOf(ClosedException.class, e.getCause());
    assertEquals("Reader is dead", e.getCause().getMessage());
  }

  private interface ServerFactory {
    ServerJocket create(int port) throws IOException;
  }

  /**
   * Starts a PeerProcess connecting to a new server, and returns the server's
   * end of the socket once the peer has sent its first byte.
   */
  private JocketSocket connectPeer(ServerFactory factory) throws Exception {
    srv = factory.create(0);
    FutureTask<JocketSocket> accept = new FutureTask<>(srv::accept);
    Thread t = new Thread(accept, "accept");
    t.setDaemon(true);
    t.start();

    peer = java(PeerProcess.class.getName(), String.valueOf(srv.getLocalPort()))
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start();
    JocketSocket s = accept.get(TIMEOUT, TimeUnit.NANOSECONDS);
    assertEquals('r', s.getInputStream().read());
    return s;
  }

  /**
   * Returns the pid of a process that has exited.
   */
  private static long deadPid() throws Exception {
    Process p = java("-version")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
    p.waitFor();
    return p.pid();
  }

  private static ProcessBuilder java(String... args) {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    String[] cmd = new String[args.length + 3];
    cmd[0] = java;
    cmd[1] = "-cp";
    cmd[2] = System.getProperty("java.class.path");
    System.arraycopy(args, 0, cmd, 3, args.length);
    return new ProcessBuilder(cmd);
  }
}
