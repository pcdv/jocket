package jocket.wait;

import java.util.concurrent.locks.LockSupport;

public class BusyYieldSleep implements WaitStrategy {

  public static final int SPIN = 1000000;

  public static final int YIELD = SPIN + 1;

  public static final int SLEEP = YIELD + 1;

  private int counter;

  @Override
  public void pauseWhile(int seq) {
    int counter = this.counter++;
    if (counter < SPIN)
      return;
    else if (counter < YIELD)
      Thread.yield();
    else if (counter < SLEEP)
      LockSupport.parkNanos(1);
    else {
      this.counter = 0;
    }
  }

  @Override
  public void reset() {
    counter = 0;
  }
}
