package jocket.wait;

import java.util.concurrent.locks.LockSupport;

public class BusyYieldSleep implements WaitStrategy {

  private int counter;

  @Override
  public void pause() {
    int counter = this.counter++;
    if (counter < 10000)
      return;
    if (counter < 12000)
      Thread.yield();
    if (counter < 100000)
      LockSupport.parkNanos(10);
    else {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
      }
    }
  }

  @Override
  public void reset() {
    counter = 0;
  }
}
