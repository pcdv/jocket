package jocket.impl;

import java.nio.ByteBuffer;

public class JocketWriter extends AbstractJocketImpl {

	/**
	 * The sequence number of the next packet to write. Accessed only by the
	 * writer thread.
	 */
	private int wseq;

	public JocketWriter(ByteBuffer buf, int npackets) {
		super(buf, npackets);
	}

	public int write(byte[] data, int off, int len) {
		final int rseq = rseq();
		// cannot write if all packets are written and the reader didn't read them
		if (wseq - rseq >= npackets)
			return 0;

		final int head = head(rseq);
		final int writable = Math.min(getAvailableSpace(rseq, head), len);
		if (writable > 0) {
			final int idx = wseq & packetMask;
			buf.putInt(PACKET_INFO + idx * LEN_PACKET_INFO, head);
			buf.putInt(PACKET_INFO + idx * LEN_PACKET_INFO + 4, writable);
			buf.position(head);
			buf.put(data, off, writable);
			buf.putInt(WSEQ, ++wseq);
		}
		return writable;
	}

	/**
	 * Returns the offset where next data can be written.
	 * 
	 * @param rseq
	 */
	private int head(int rseq) {
		// if all packets are read, we can start at 0
		if (wseq == rseq)
			return 0;

		final int idx = (wseq - 1) & packetMask;

		return buf.getInt(PACKET_INFO + idx * LEN_PACKET_INFO)
		    + buf.getInt(PACKET_INFO + idx * LEN_PACKET_INFO + 4);
	}

	private long start(int packet) {
		return buf.getInt(PACKET_INFO + (packet & packetMask) * LEN_PACKET_INFO);
	}

	/**
	 * Returns how many bytes can be written in one chunk at current position.
	 * 
	 * @param rseq current seqnum of the reader
	 * @param head current data head
	 */
	private int getAvailableSpace(int rseq, int head) {
		int space = (int) (capacity - (head - start(rseq)));
		if (space <= 0)
			return 0;

		int spaceBeforeEnd = capacity - (head & dataMask);
		if (space > spaceBeforeEnd)
			return spaceBeforeEnd;

		return space;
	}

	private int rseq() {
		return buf.getInt(RSEQ);
	}

	/**
	 * Returns how many bytes can be written (mainly for test purposes).
	 */
	public int available() {
		// cannot write if all packets are written and the reader didn't read any
		int rseq = rseq();
		if (wseq - rseq >= npackets)
			return 0;
		return getAvailableSpace(rseq, head(rseq));
	}
}
