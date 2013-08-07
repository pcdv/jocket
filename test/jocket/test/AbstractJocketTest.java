package jocket.test;

import java.nio.ByteBuffer;

import jocket.impl.Const;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class AbstractJocketTest {

	private final int readBufSize = 8192;

	protected JocketWriter w;
	protected JocketReader r;

	protected void init(int npackets, int capacity) {
		ByteBuffer buf = ByteBuffer.allocate(Const.PACKET_INFO + npackets
		    * Const.LEN_PACKET_INFO + capacity);
		w = new JocketWriter(buf, npackets);
		r = new JocketReader(buf, npackets);
	}

	protected int write(String... strs) {
		int total = 0;
		for (String s : strs)
			total += w.write(s.getBytes(), 0, s.length());
		return total;
	}

	protected String read() {
		byte[] buf = new byte[readBufSize];
		return read(buf, 0, buf.length);
	}

	protected String read(byte[] buf, int off, int len) {
		return new String(buf, off, r.read(buf, off, len));
	}
}
