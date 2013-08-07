package jocket.impl;

import java.nio.ByteBuffer;

public class JocketWriter extends AbstractJocketImpl {

	/**
	 * The sequence number of the next packet to write.
	 */
	private int wseq;

	public JocketWriter(ByteBuffer buf, int npackets) {
		super(buf, npackets);
	}

	public int write(byte[] data, int off, int len) {
		int rseq = getReaderPacket();

		// cannot write if all packets are written and the reader didn't read any
		if (wseq - rseq >= npackets)
			return 0;

		int infoIndex = wseq & packetMask;
		int writable = Math.min(getAvailableSpace(), len);
		final int head = head();
		if (writable > 0) {
			int start = head & dataMask;
			buf.putInt(PACKET_INFO + infoIndex * LEN_PACKET_INFO, start);
			buf.putInt(PACKET_INFO + infoIndex * LEN_PACKET_INFO + 4, writable);
			buf.position(start);
			buf.put(data, off, writable);
			buf.putInt(WSEQ, ++wseq);
		}
		return writable;
	}

	private int head() {
		if (wseq == buf.getInt(RSEQ))
			return 0;

		int windex = (wseq - 1) & packetMask;

		return buf.getInt(PACKET_INFO + windex * LEN_PACKET_INFO)
		    + buf.getInt(PACKET_INFO + windex * LEN_PACKET_INFO + 4);
	}

	private long tail() {
		int rseq = buf.getInt(RSEQ);
		int rindex = rseq & packetMask;
		return buf.getInt(PACKET_INFO + rindex * LEN_PACKET_INFO);
	}

	private int getAvailableSpace() {
		final int head = head();
		int space = (int) (capacity - (head - tail()));
		if (space <= 0)
			return 0;

		int spaceBeforeEnd = capacity - (head & dataMask);
		if (space > spaceBeforeEnd)
			return spaceBeforeEnd;

		return space;
	}

	private int getReaderPacket() {
		return buf.getInt(RSEQ);
	}

	public int available() {
		// cannot write if all packets are written and the reader didn't read any
		int rseq = getReaderPacket();
		if (wseq - rseq >= npackets)
			return 0;
		return getAvailableSpace();
	}
}
