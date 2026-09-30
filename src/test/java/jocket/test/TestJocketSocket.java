package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jocket.impl.ClosedException;
import jocket.net.JocketSocket;

import org.junit.jupiter.api.Test;

public class TestJocketSocket extends AbstractJocketSocketTest {

  @Test
  public void testWriteRead() throws Exception {
    OutputStream out = c.getOutputStream();
    out.write("hello".getBytes());
    out.flush();
    byte[] buf = new byte[100];
    int len = s.getInputStream().read(buf);
    assertEquals(5, len);
    assertEquals("hello", new String(buf, 0, 5));
  }

  @Test
  public void testCloseOutput() throws Exception {
    c.getOutputStream().close();
    assertEquals(-1, s.getInputStream().read());
  }

  @Test
  public void testCloseInput() throws Exception {
    c.getInputStream().close();
    try {
      s.getOutputStream().write(22);
      fail("");
    } catch (ClosedException e) {
    }
  }

  /**
   * Used to reproduce a bug. Warning: this test works only if the jocket buffer
   * capacity is big enough.
   */
  @Test
  public void testUnderflow() throws Exception {
    byte[] buf = new byte[300000];
    for (int i = 0; i < 100; i++) {
      c.getOutputStream().write(buf);
      c.getWriter().flush();
      new DataInputStream(s.getInputStream()).readFully(buf);
    }
  }

  /**
   * A zero-copy send() used not to signal the futex, leaving the reader asleep.
   */
  @Test
  public void testZeroCopySendWakesReader() throws Exception {
    FutureTask<Integer> read = new FutureTask<>(() -> c.getInputStream().read(new byte[16]));
    Thread reader = new Thread(read, "reader");
    reader.setDaemon(true);
    reader.start();
    Thread.sleep(100); // lets the reader go to sleep

    ByteBuffer b = s.getWriter().newPacket(4);
    b.put("ping".getBytes());
    s.getWriter().send(b);
    assertEquals(4, read.get(5, TimeUnit.SECONDS));
  }

  /**
   * Closing a socket must wake up a thread blocked reading it (on the futex
   * under Linux).
   */
  @Test
  public void testCloseWakesBlockedReader() throws Exception {
    FutureTask<Integer> read = startRead(c);
    c.close();
    assertEquals(-1, read.get(5, TimeUnit.SECONDS));
  }

  /**
   * Closing a socket must wake up the peer blocked reading it (on the futex
   * under Linux).
   */
  @Test
  public void testPeerCloseWakesBlockedReader() throws Exception {
    FutureTask<Integer> read = startRead(c);
    s.close();
    assertEquals(-1, read.get(5, TimeUnit.SECONDS));
  }

  /**
   * Starts a thread reading specified socket, and gives it time to go to
   * sleep.
   */
  private static FutureTask<Integer> startRead(JocketSocket sock) throws InterruptedException {
    FutureTask<Integer> read = new FutureTask<>(() -> sock.getInputStream().read(new byte[16]));
    Thread reader = new Thread(read, "reader");
    reader.setDaemon(true);
    reader.start();
    Thread.sleep(100);
    return read;
  }
}
