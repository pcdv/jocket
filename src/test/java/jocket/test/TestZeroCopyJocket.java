package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

/**
 * Tests zero-copy features of Jocket.
 * 
 * @author pcdv
 */
public class TestZeroCopyJocket extends AbstractJocketTest {

  /**
   * Write data and read it before new iteration.
   */
  @Test
  public void testWriteAndReadNTimes() throws Exception {
    init(4, 128);

    for (int i = 0; i < 100; i++) {
      String data = "xxx" + i;
      ByteBuffer b = w.newPacket(16);
      b.put(data.getBytes());
      w.send(b);

      b = r.nextPacket();
      byte[] arr = new byte[b.remaining()];
      b.get(arr);
      assertEquals(data, new String(arr));
      r.release(b);
    }
  }

  @Test
  public void testWriteBatchAndRead() throws Exception {
    init(128, 1024);

    int ITER = 2;

    for (int i = 0; i < ITER; i++) {
      String data = "xxx" + i;
      ByteBuffer b = w.newPacket(16);
      b.put(data.getBytes());
      w.send(b);
    }

    for (int i = 0; i < ITER; i++) {
      String data = "xxx" + i;
      ByteBuffer b = r.nextPacket();
      byte[] arr = new byte[b.remaining()];
      b.get(arr);
      assertEquals(data, new String(arr));
      r.release(b);
    }
  }

  /**
   * release() of a partly read packet used to store a position relative to the
   * packet instead of an absolute one.
   */
  @Test
  public void testPartialRelease() throws Exception {
    init(4, 128);
    write("AAAA", "BCDEFG"); // the 2nd packet does not start at 0
    read("AAAA");

    ByteBuffer b = r.nextPacket();
    assertEquals("BC", get(b, 2));
    r.release(b);

    b = r.nextPacket();
    assertEquals("DEFG", get(b, b.remaining()));
    r.release(b);
  }

  /**
   * newPacket() used to fail once the write position had gone around the
   * buffer, which happens when the reader never catches up completely.
   */
  @Test
  public void testNewPacketAfterWrap() throws Exception {
    init(64, 64);
    int sent = 0, received = 0;
    while (received < 100) {
      ByteBuffer b = w.newPacket(16);
      if (b != null) {
        assertTrue(b.remaining() >= 16);
        for (int i = 0; i < 16; i++)
          b.put((byte) sent);
        w.send(b);
        sent++;
      }

      // the reader stays behind so that positions are never reset
      if (b == null || sent - received > 2) {
        ByteBuffer p = r.nextPacket();
        assertEquals(16, p.remaining());
        while (p.hasRemaining())
          assertEquals((byte) received, p.get());
        r.release(p);
        received++;
      }
    }
  }

  /**
   * newPacket() used to silently drop the data written (but not flushed) by
   * write().
   */
  @Test
  public void testNewPacketAfterUnflushedWrite() throws Exception {
    init(4, 128);
    write0("abc");
    ByteBuffer b = w.newPacket(16);
    b.put("def".getBytes());
    w.send(b);
    read("abc", "def");
  }

  private static String get(ByteBuffer b, int len) {
    byte[] arr = new byte[len];
    b.get(arr);
    return new String(arr);
  }
}
