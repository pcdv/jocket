package jocket.impl;

/**
 * Layout of an exchange file. The header is accessed in native byte order.
 * Sequence numbers and positions are longs, so they never overflow.
 */
public interface Const {

  int _CACHELINE = 64;

  /**
   * Position at which meta-data starts.
   */
  int META = 0;

  int META_MAX_PACKETS = META;

  int META_CAPACITY = META + 4;

  int FUTEX = META + _CACHELINE;

  /**
   * Position at which the writer puts its sequence number (a long).
   */
  int WSEQ = FUTEX + _CACHELINE;

  /**
   * Set to 1 by the writer when it is closed. In the cache line of WSEQ, which
   * the reader fetches anyway.
   */
  int WCLOSED = WSEQ + 8;

  /**
   * Position at which the reader puts its sequence number (a long).
   */
  int RSEQ = WSEQ + _CACHELINE;

  /**
   * Set to 1 by the reader when it is closed. In the cache line of RSEQ, which
   * the writer fetches anyway.
   */
  int RCLOSED = RSEQ + 8;

  /**
   * Unused.
   */
  int RESERVED = RSEQ + _CACHELINE;

  /**
   * Position at which packet information starts.
   */
  int PACKET_INFO = RESERVED + _CACHELINE;

  /**
   * Length of data describing a packet: its position (a long) and its length
   * (an int, at offset 8). Padded to cacheline size.
   */
  int LEN_PACKET_INFO = _CACHELINE;

}
