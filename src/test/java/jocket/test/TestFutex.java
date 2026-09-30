package jocket.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jocket.futex.Futex;
import jocket.impl.Const;
import jocket.net.JocketFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Low-level tests of the JNI futex implementation.
 *
 * @author pcdv
 */
public class TestFutex implements Const {

  private static final long LONG_TIMEOUT = TimeUnit.SECONDS.toNanos(10);

  private JocketFile jf;

  private Futex futex;

  @BeforeEach
  public void setUp() throws IOException {
    assumeTrue(Futex.isAvailable(), "JNI futex library not available");
    jf = new JocketFile(16, 1024);
    futex = new Futex(jf.getBuffer(), FUTEX);
  }

  @AfterEach
  public void tearDown() {
    if (jf != null)
      jf.close();
  }

  @Test
  public void testSignalWakesWaiter() throws Exception {
    for (int i = 0; i < 10; i++) {
      CountDownLatch latch = startAwait(LONG_TIMEOUT);
      assertFalse(latch.await(30, TimeUnit.MILLISECONDS), "Woke up too soon");
      assertEquals(-1, jf.getBuffer().getInt(FUTEX));
      futex.signal();
      assertTrue(latch.await(1, TimeUnit.SECONDS), "Still sleeping");
      assertEquals(0, jf.getBuffer().getInt(FUTEX));
    }
  }

  @Test
  public void testPendingSignal() throws Exception {
    futex.signal();
    assertEquals(1, jf.getBuffer().getInt(FUTEX));
    long t0 = System.nanoTime();
    futex.await(LONG_TIMEOUT);
    assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(1), "Waited despite the signal");
    assertEquals(0, jf.getBuffer().getInt(FUTEX));
  }

  @Test
  public void testTimeout() throws Exception {
    long t0 = System.nanoTime();
    futex.await(TimeUnit.MILLISECONDS.toNanos(50));
    long elapsed = System.nanoTime() - t0;
    assertTrue(elapsed >= TimeUnit.MILLISECONDS.toNanos(40), "Returned too soon: " + elapsed);
    assertEquals(0, jf.getBuffer().getInt(FUTEX));
  }

  /**
   * Starts a thread calling await(), which then counts down the latch.
   */
  private CountDownLatch startAwait(long timeoutNanos) {
    CountDownLatch latch = new CountDownLatch(1);
    Thread waiter = new Thread(() -> {
      futex.await(timeoutNanos);
      latch.countDown();
    }, "waiter");
    waiter.setDaemon(true);
    waiter.start();
    return latch;
  }
}
