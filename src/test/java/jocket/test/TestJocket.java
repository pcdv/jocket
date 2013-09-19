package jocket.test;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;

public class TestJocket extends AbstractJocketTest {

  /**
   * Basic test case: write data and read it back.
   */
  @Test
  public void testBasic() throws Exception {
    init(4, 128);
    byte[] data = new byte[10];
    assertEquals(0, r.read(data, 0, data.length));
    assertEquals(4, write("toto"));
    assertEquals("toto", read());
    assertEquals("", read());
  }

  /**
   * Check that consecutive writes without flushing end up in a single packet.
   */
  @Test
  public void testNoFlush() throws Exception {
    init(128, 8);
    assertEquals(5, write0("11111"));
    assertEquals(3, write0("22222"));

    // flush is now automatic when reaching end of buffer
    // assertEquals(0, r.available());
    // w.flush();

    assertEquals(8, r.available());
    assertEquals("11111222", read());
  }

  @Test
  public void testNoFlush2() throws Exception {
    testNoFlush();
    testNoFlush();
  }

  /**
   * Check that no data can be written when the maximum number of packets is
   * written.
   */
  @Test
  public void testFullPackets() throws Exception {
    init(4, 128);
    assertEquals(16, write("1111", "2222", "3333", "4444"));
    assertEquals(0, write("5555"));
    assertEquals("1111", read());
    assertEquals(4, write("6666"));
    assertEquals("2222", read());
    assertEquals("3333", read());
    assertEquals("4444", read());
    assertEquals("6666", read());
  }

  /**
   * Check that no data can be written when the byte buffer is full (and the
   * maximum number of packets is not reached).
   */
  @Test
  public void testFullData() throws Exception {
    init(128, 32);
    assertEquals(32, w.available());
    assertEquals(0, r.available());
    assertEquals(32,
        write("0123456789", "0123456789", "0123456789", "0123456789"));
    assertEquals(0, w.available());
    assertEquals(32, r.available());
    assertEquals(0, write("foo"));
  }

  @Test
  public void testFullData2() throws Exception {
    init(128, 8);
    assertEquals(8, w.available());
    assertEquals(0, r.available());
    assertEquals(5, write("11111"));
    assertEquals(3, write("22222"));
  }

  @Test
  public void testFullData3() throws Exception {
    init(128, 8);
    write("12345678");
    read(2, "12");
    assertEquals(2, w.available());
    assertEquals(2, write0("xx"));
    assertEquals(0, w.available());
    write0("yy");
  }

  @Test
  public void testPartialRead() throws Exception {
    testFullData();
    byte[] buf = new byte[100];
    // at this point, the buffer is full

    // read a part of first packet, this should free up 2 bytes for writing
    assertEquals("01", read(buf, 10, 2));
    assertEquals(2, w.available());
    assertEquals(30, r.available());

    assertEquals("23", read(buf, 10, 2));
    assertEquals(4, w.available());
    assertEquals(28, r.available());

    assertEquals("456789", read());
    assertEquals(10, w.available());
    assertEquals(22, r.available());

    assertEquals("0123456789", read());
    assertEquals("0123456789", read());
    assertEquals("01", read());
  }

  /**
   * Variant of the above.
   */
  @Test
  public void testPartialRead2() throws Exception {
    testFullData();
    byte[] buf = new byte[100];
    // at this point, the buffer is full

    // read a part of first packet, this should free up 2 bytes for writing
    assertEquals("01", read(buf, 10, 2));
    assertEquals(2, w.available());
    assertEquals(30, r.available());

    // fill the 2 bytes
    assertEquals(2, write("ABCDEF"));

    // read everything
    assertEquals("23456789", read());
    assertEquals("0123456789", read());
    assertEquals("0123456789", read());
    assertEquals("01", read());
    assertEquals("AB", read());
  }

  @Test
  public void testWrap() throws Exception {
    init(128, 8);
    assertEquals(6, write("ABCDEF"));
    assertEquals("AB", read(new byte[2], 0, 2));
    assertEquals(2, write("GHIJKL"));
    assertEquals("CDEF", read());
    assertEquals("GH", read());
  }

  /**
   * Reproduced bug when writing (without flushing) data at the end of data
   * buffer, and flushing only after writing some additional bytes.
   */
  @Test
  public void testWrap2() throws Exception {
    init(128, 8);
    assertEquals(6, write("ABCDEF"));

    // overflow buffer
    assertEquals(2, write(false, "GHIJKL"));
    assertEquals("ABCDEF", read());
    assertEquals(4, write("IJKL"));

    assertEquals("GH", read());
    assertEquals("IJKL", read());
  }

  @Test
  @Deprecated
  public void testAutoReset() throws Exception {
    init(128, 8);
    r.setResetSeqNum(0);
    w.setResetSeqNum(0);

    // write and read data
    write("ABC");
    read("ABC");

    // buffer should be reset during next write
    write("Z");
    // assertEquals(1, w.getSeqNum());
    assertEquals(1, w.getPosition());

    read("Z");
  }

  @Test
  @Deprecated
  public void testAutoReset2() throws Exception {
    init(128, 8);
    r.setResetSeqNum(0);
    w.setResetSeqNum(0);

    // write and read data
    write("ABC");
    assertEquals(1, w.getSeqNum());
    assertEquals(3, w.getPosition());
    read("ABC");

    // buffer should be reset during next write
    write("Z");
    // assertEquals(1, w.getSeqNum());
    assertEquals(1, w.getPosition());
    write("X");
    // assertEquals(2, w.getSeqNum());
    assertEquals(2, w.getPosition());
    write("Y");
    // assertEquals(3, w.getSeqNum());
    assertEquals(3, w.getPosition());

    read("Z");
    read("X");
    read("Y");
  }

  @Test
  public void testUnderflow() throws Exception {
    init(64, 8);

    write(3, 5, 5, 12);
    read(2, 4, 2, 0, 0);

    write(5);
    flush();
    read(1, 1);

    write(3, 3);
    flush();
    write(5);
    read(1, 1, 1, 0);

    write(30);
    flush();
    read(3, 2);
  }

  public void testBigSeqNum() throws Exception {
    init(64, 4);

    for (int i = 0; i < 1000; i++) {
      read(1);
      read(1);
      write(1);
      write(1);
      write(1);
      flush();
    }
  }
}
