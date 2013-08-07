package jocket.test;

import static junit.framework.Assert.assertEquals;

import java.nio.ByteBuffer;

import jocket.impl.Const;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

import org.junit.Before;
import org.junit.Test;

public class TestJocket {

	private JocketWriter w;
	private JocketReader r;
	private final int readBufSize = 8192;

	@Before
	public void setUp() throws Exception {
	}

	private void init(int npackets, int capacity) {
		ByteBuffer buf = ByteBuffer.allocate(Const.PACKET_INFO + npackets
		    * Const.LEN_PACKET_INFO + capacity);
		w = new JocketWriter(buf, npackets);
		r = new JocketReader(buf, npackets);
	}

	/**
	 * Basic test case.
	 */
	@Test
	public void testBasic() {
		init(4, 128);
		byte[] data = new byte[10];
		assertEquals(0, r.read(data, 0, data.length));
		assertEquals(4, write("toto"));
		assertEquals("toto", read());
		assertEquals("", read());
	}

	private int write(String... strs) {
		int total = 0;
		for (String s : strs)
			total += w.write(s.getBytes(), 0, s.length());
		return total;
	}

	private String read() {
		byte[] buf = new byte[readBufSize];
		return read(buf, 0, buf.length);
	}

	private String read(byte[] buf, int off, int len) {
		return new String(buf, off, r.read(buf, off, len));
	}

	/**
	 * Check that no data can be written when the maximum number of packets is
	 * written (but not read).
	 */
	@Test
	public void testFullPackets() throws Exception {
		init(4, 128);
		assertEquals(16, write("1111", "2222", "3333", "4444"));
		assertEquals(0, write("5555"));
		assertEquals("1111", read());
		assertEquals(4, write("6666"));
		assertEquals("2222", read());
		assertEquals("3333", read());
		assertEquals("4444", read());
		assertEquals("6666", read());
	}

	@Test
	public void testFullData() throws Exception {
		init(128, 32);
		assertEquals(32, w.available());
		assertEquals(0, r.available());
		assertEquals(32,
		    write("0123456789", "0123456789", "0123456789", "0123456789"));
		assertEquals(0, w.available());
		assertEquals(32, r.available());
	}

	@Test
	public void testPartialRead() throws Exception {
		testFullData();
		byte[] buf = new byte[100];
		assertEquals("01", read(buf, 10, 2));
		assertEquals(2, w.available());
		assertEquals(30, r.available());

		assertEquals("23", read(buf, 10, 2));
		assertEquals(4, w.available());
		assertEquals(28, r.available());

		assertEquals("456789", read());
		assertEquals(10, w.available());
		assertEquals(22, r.available());

		assertEquals("0123456789", read());
		assertEquals("0123456789", read());
		assertEquals("01", read());
	}
}
