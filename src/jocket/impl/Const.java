package jocket.impl;

public interface Const {

	int _CACHELINE = 64;

	int LEN_PACKET_INFO = 8;

	int META = 0;

	int WSEQ = META + _CACHELINE;

	int RSEQ = WSEQ + _CACHELINE;

	int PACKET_INFO = RSEQ + _CACHELINE;

}
