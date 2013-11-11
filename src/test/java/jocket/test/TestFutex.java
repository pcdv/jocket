package jocket.test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jocket.futex.Futex;
import jocket.impl.Const;
import jocket.net.JocketFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Misc low-level tests to validate the JNI futex implementation.
 *
 * @author pcdv
 */
public class TestFutex implements Const {

  private Futex futex;

  private MappedByteBuffer buf;

  @Before
  public void setUp() throws IOException {
    JocketFile jf = new JocketFile(16, 1024);
    buf = jf.getBuffer();
    futex = new Futex(jf.getBuffer(), FUTEX, RSEQ);
  }

  @After
  public void tearDown() {

  }

  private void checkValues(int futex, int rseq) {
    assertEquals(futex, this.futex.getInt(FUTEX));
    assertEquals(rseq, this.futex.getInt(RSEQ));
  }

  @Test
  public void testAddressResolution() throws Exception {
    futex.setInt(0, 42);
    assertEquals(42, buf.getInt(0));
    futex.setInt(8, 43);
    assertEquals(43, buf.getInt(8));
    futex.setInt(128, 42);
    assertEquals(42, buf.getInt(128));
    futex.setInt(136, 43);
    assertEquals(43, buf.getInt(136));
  }

  @Test
  public void testFutexAwait() throws Exception {
    for (int i = 0; i < 10; i++) {
      final CountDownLatch latch = futureAwait();
      checkWaiting(latch);
      futex.signal(-1);
      checkAwake(latch);
    }
  }

  @Test
  public void testFutexPause() throws Exception {
    for (int i = 0; i < 10; i++) {

      checkValues(0, i);
      final CountDownLatch latch = futurePause(i);
      Thread.sleep(10);

      checkWaiting(latch);
      checkValues(-1, i);

      futex.signal(i);
      checkWaiting(latch);
      buf.putInt(RSEQ, i + 1);
      futex.signal(i + 1);
      checkAwake(latch);
      assertEquals(0, buf.getInt(FUTEX));
    }
  }

  /**
   * Starts a new thread and make it call await(), then count down the latch.
   */
  private CountDownLatch futureAwait() {
    final CountDownLatch latch = new CountDownLatch(1);
    Thread reader = new Thread() {
      @SuppressWarnings("deprecation")
      @Override
      public void run() {
        futex.await();
        latch.countDown();
      }
    };
    reader.start();
    return latch;
  }

  /**
   * Starts a new thread and make it call pauseWhile(seq), then count down the
   * latch.
   */
  private CountDownLatch futurePause(final int seq) {
    final CountDownLatch latch = new CountDownLatch(1);
    Thread reader = new Thread() {
      @Override
      public void run() {
        futex.pauseWhile(seq);
        latch.countDown();
      }
    };
    reader.start();
    return latch;
  }

  /**
   * Check that specified latch has been counted down, ie. the thread has
   * finished waiting.
   */
  private void checkAwake(final CountDownLatch latch)
      throws InterruptedException {
    assertTrue("Still sleeping", latch.await(5, TimeUnit.MILLISECONDS));
  }

  /**
   * Check that specified latch has been counted down, ie. the thread has is
   * still waiting to be woken up.
   */
  private void checkWaiting(final CountDownLatch latch)
      throws InterruptedException {
    assertFalse("Woke up too soon", latch.await(30, TimeUnit.MILLISECONDS));
  }
}
