package jocket.wait;

import java.util.concurrent.locks.LockSupport;

public class BusyYieldSleep implements WaitStrategy {

  int counter;

  @Override
  public void pause() {
    if (counter++ < 100)
      return;
    if (counter < 200)
      Thread.yield();
    if (counter < 20000)
      LockSupport.parkNanos(1000);
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
