package jocket.wait;

import java.util.concurrent.locks.LockSupport;

/**
 * Progressive backoff: spins for a while (lowest latency), then yields to
 * other threads, then sleeps for increasing durations (lowest CPU usage).
 * Returns to spinning when reset, i.e. after each successful read or write.
 * <p>
 * Not thread-safe: an instance must not be shared.
 */
public class BusyYieldSleep implements WaitStrategy {

  public static final int DEFAULT_SPINS = 10_000;

  public static final int DEFAULT_YIELDS = 100;

  public static final long DEFAULT_MAX_SLEEP_NANOS = 1_000_000;

  private static final long MIN_SLEEP_NANOS = 1_000;

  private final int spins;

  private final int yields;

  private final long maxSleepNanos;

  private int counter;

  private long sleepNanos = MIN_SLEEP_NANOS;

  public BusyYieldSleep() {
    this(DEFAULT_SPINS, DEFAULT_YIELDS, DEFAULT_MAX_SLEEP_NANOS);
  }

  /**
   * @param spins number of pauses spent spinning
   * @param yields number of pauses then spent yielding
   * @param maxSleepNanos maximum duration of the following pauses, which sleep
   *        1 microsecond at first, then twice as long each time
   */
  public BusyYieldSleep(int spins, int yields, long maxSleepNanos) {
    if (spins < 0 || yields < 0 || maxSleepNanos < MIN_SLEEP_NANOS)
      throw new IllegalArgumentException();
    this.spins = spins;
    this.yields = yields;
    this.maxSleepNanos = maxSleepNanos;
  }

  @Override
  public void pause() {
    if (counter < spins) {
      counter++;
      Thread.onSpinWait();
    }
    else if (counter < spins + yields) {
      counter++;
      Thread.yield();
    }
    else {
      LockSupport.parkNanos(sleepNanos);
      sleepNanos = Math.min(sleepNanos * 2, maxSleepNanos);
    }
  }

  @Override
  public void reset() {
    counter = 0;
    sleepNanos = MIN_SLEEP_NANOS;
  }
}
