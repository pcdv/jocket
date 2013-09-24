package jocket.wait;

import jocket.futex.Futex;

public class FutexWaitStrategy implements WaitStrategy {

  private final Futex futex;

  private int counter;

  public FutexWaitStrategy(Futex futex) {
    this.futex = futex;
  }

  @Override
  public void pauseWhile(int seq) {
    final int counter = this.counter++;
    if (counter < 200) {
      Futex.x86pause();
    }
//    else if (counter < 1100) {
//      Thread.yield();
//    }
    else
      futex.await();
  }

  @Override
  public void reset() {
    counter = 0;
  }

}
