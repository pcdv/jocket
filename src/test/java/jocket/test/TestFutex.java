package jocket.test;

import static junit.framework.Assert.*;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jocket.futex.Futex;
import jocket.impl.Const;
import jocket.net.JocketFile;

import org.junit.Before;
import org.junit.Test;

public class TestFutex implements Const {

  private Futex futex;

  private MappedByteBuffer buf;

  @Before
  public void setUp() throws IOException {
    JocketFile jf = new JocketFile(16, 1024);
    buf = jf.getBuffer();
    futex = new Futex(jf.getBuffer(), FUTEX, RSEQ);
  }

  @Test
  public void testFutexAwait() throws Exception {
    for (int i = 0; i < 10; i++) {
      final CountDownLatch latch = futureAwait();
      assertFalse(latch.await(50, TimeUnit.MILLISECONDS));
      futex.signal(-1);
      assertTrue(latch.await(10, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  public void testFutexPause() throws Exception {
    for (int i = 0; i < 10; i++) {
      final CountDownLatch latch = futurePause(i);
      assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
      futex.signal(i);
      assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
      buf.putInt(RSEQ, i + 1);
      futex.signal(i + 1);
      assertTrue(latch.await(10, TimeUnit.MILLISECONDS));
      assertEquals(0, buf.getInt(FUTEX));
    }
  }

  private CountDownLatch futureAwait() {
    final CountDownLatch latch = new CountDownLatch(1);
    Thread reader = new Thread() {
      @Override
      public void run() {
        futex.await();
        System.out.println("Done await");
        latch.countDown();
      }
    };
    reader.start();
    return latch;
  }

  private CountDownLatch futurePause(final int seq) {
    final CountDownLatch latch = new CountDownLatch(1);
    Thread reader = new Thread() {
      @Override
      public void run() {
        futex.pause(seq);
        System.out.println("Done pause");
        latch.countDown();
      }
    };
    reader.start();
    return latch;
  }

}
