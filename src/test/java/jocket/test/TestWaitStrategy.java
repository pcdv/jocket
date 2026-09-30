package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import jocket.wait.BusyYieldSleep;

import org.junit.jupiter.api.Test;

public class TestWaitStrategy {

  /**
   * The strategy used to spin forever, with one yield and one 1ns sleep every
   * million pauses: 100% of a CPU while waiting.
   */
  @Test
  public void testSleepsAfterSpinningAndYielding() {
    BusyYieldSleep wait = new BusyYieldSleep(100, 10, TimeUnit.MILLISECONDS.toNanos(1));
    for (int i = 0; i < 110; i++)
      wait.pause();

    // 1, 2, 4... 512 microseconds, then 1 millisecond
    long t0 = System.nanoTime();
    for (int i = 0; i < 20; i++)
      wait.pause();
    long elapsed = System.nanoTime() - t0;
    assertTrue(elapsed >= TimeUnit.MILLISECONDS.toNanos(10), "slept only " + elapsed + "ns");
    assertTrue(elapsed < TimeUnit.SECONDS.toNanos(2), "slept " + elapsed + "ns");
  }

  @Test
  public void testResetSpinsAgain() {
    BusyYieldSleep wait = new BusyYieldSleep(1000, 0, TimeUnit.MILLISECONDS.toNanos(100));
    for (int i = 0; i < 1010; i++)
      wait.pause();
    wait.reset();

    long t0 = System.nanoTime();
    for (int i = 0; i < 1000; i++)
      wait.pause();
    long elapsed = System.nanoTime() - t0;
    assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(50), "paused " + elapsed + "ns");
  }
}
