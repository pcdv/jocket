package jocket.wait;

/**
 * Strategy used to wait for some time when data could not be read or written:
 * by JocketReader.readBlocking() when the futex is not available, and by
 * JocketOutputStream when the buffer is full.
 * 
 * @author pcdv
 */
public interface WaitStrategy {

  /**
   * Pauses for some time.
   */
  void pause();

  /**
   * Resets the strategy. Called when data has been read or written
   * successfully.
   */
  void reset();

}
