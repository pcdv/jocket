package jocket.impl;

public interface Const {

	int _CACHELINE = 64;

	/**
	 * Position at which meta-data starts.
	 */
	int META = 0;

	/**
	 * Position at which the writer puts its sequence number.
	 */
	int WSEQ = META + _CACHELINE;

	/**
	 * Position at which the reader puts its sequence number.
	 */
	int RSEQ = WSEQ + _CACHELINE;

	/**
	 * Position at which packet information starts.
	 */
	int PACKET_INFO = RSEQ + _CACHELINE;

	/**
	 * Length of data describing a packet. Two ints: position and length.
	 */
	int LEN_PACKET_INFO = 8;

}
