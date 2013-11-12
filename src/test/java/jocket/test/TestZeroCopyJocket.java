package jocket.test;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

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

}
