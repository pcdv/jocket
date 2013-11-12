package jocket.test;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

public class TestDataConsistency extends AbstractJocketSocketTest {

  Random random = new Random();

  @Test
  public void test() {
    final byte[] sequence = genRandomSequence();

    new Thread("Writer") {
      @Override
      public void run() {
        OutputStream out = s.getOutputStream();
        try {
          for (int pos = 0; pos < sequence.length;) {
            int len = Math.min(random.nextInt(500), sequence.length - pos);
            out.write(sequence, pos, len);
            if (random.nextInt() % 3 == 0)
              out.flush();
            pos += len;
          }
          out.flush();
          // out.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }.start();

    byte[] buf = new byte[100];
    ByteArrayOutputStream bout = new ByteArrayOutputStream(sequence.length);

    while (bout.size() < sequence.length) {
      try {
        int len = c.getInputStream().read(buf);
        if (len < 0)
          break;
        bout.write(buf, 0, len);
      } catch (IOException e) {
        e.printStackTrace();
        break;
      }
    }

    assertEquals(sequence.length, bout.size());
    assertTrue(Arrays.equals(sequence, bout.toByteArray()));
  }

  private byte[] genRandomSequence() {
    byte[] res = new byte[20000000];
    for (int i = 0; i < res.length; i++)
      res[i] = (byte) random.nextInt(256);

    return res;
  }

  @Test
  public void testReproduceAutoResetBug() throws Exception {
    final OutputStream out = s.getOutputStream();
    final int ITER = 10000;
    new Thread() {
      @Override
      public void run() {
        try {
          for (int i = 0; i < ITER; i++) {
            out.write(i);
            out.flush();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }.start();

    InputStream in = c.getInputStream();
    for (int i = 0; i < ITER; i++) {
      assertEquals(i % 256, in.read());
    }

  }
}
