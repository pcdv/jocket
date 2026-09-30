package jocket.wait;

/**
 * Strategy used to wait for some time when data could not be read or written.
 * Currently used only in JockectInputStream and JockectOutputStream wrappers.
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
