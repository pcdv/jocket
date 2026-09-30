package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import jocket.impl.JocketInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TestJocketStreams extends AbstractJocketTest {

  /**
   * read(b, off, 0) used to loop forever instead of returning 0 at once, as
   * required by InputStream.
   */
  @Test
  @Timeout(5)
  public void testReadZeroBytes() throws Exception {
    init(4, 128);
    JocketInputStream in = new JocketInputStream(r);
    assertEquals(0, in.read(new byte[4], 0, 0)); // no data
    write("toto");
    assertEquals(0, in.read(new byte[4], 0, 0)); // a packet is available
    assertEquals(4, in.read(new byte[4], 0, 4));
  }
}
