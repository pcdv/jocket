package jocket.test;

import static junit.framework.Assert.assertEquals;

import java.io.DataInputStream;
import java.io.OutputStream;

import jocket.impl.ClosedException;
import junit.framework.Assert;

import org.junit.Test;

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
      Assert.fail("");
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
}
