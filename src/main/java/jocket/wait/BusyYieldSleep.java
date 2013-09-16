package jocket.wait;

import java.util.concurrent.locks.LockSupport;

public class BusyYieldSleep implements WaitStrategy {

  private int counter;

  @Override
  public void pause() {
    int counter = this.counter++;
    if (counter < 10000)
      return;
    else if (counter < 20000)
      Thread.yield();
    else if (counter < 500000)
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
