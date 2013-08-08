package jocket.test;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;

public class TestJocket extends AbstractJocketTest {

	/**
	 * Basic test case: write data and read it back.
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

	/**
	 * Check that no data can be written when the maximum number of packets is
	 * written.
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

	/**
	 * Check that no data can be written when the byte buffer is full (and the
	 * maximum number of packets is not reached).
	 */
	@Test
	public void testFullData() throws Exception {
		init(128, 32);
		assertEquals(32, w.available());
		assertEquals(0, r.available());
		assertEquals(32,
		    write("0123456789", "0123456789", "0123456789", "0123456789"));
		assertEquals(0, w.available());
		assertEquals(32, r.available());
		assertEquals(0, write("foo"));
	}

	@Test
	public void testPartialRead() throws Exception {
		testFullData();
		byte[] buf = new byte[100];
		// at this point, the buffer is full

		// read a part of first packet, this should free up 2 bytes for writing
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

	/**
	 * Variant of the above.
	 */
	@Test
	public void testPartialRead2() throws Exception {
		testFullData();
		byte[] buf = new byte[100];
		// at this point, the buffer is full

		// read a part of first packet, this should free up 2 bytes for writing
		assertEquals("01", read(buf, 10, 2));
		assertEquals(2, w.available());
		assertEquals(30, r.available());

		// fill the 2 bytes
		assertEquals(2, write("ABCDEF"));

		// read everything
		assertEquals("23456789", read());
		assertEquals("0123456789", read());
		assertEquals("0123456789", read());
		assertEquals("01", read());
		assertEquals("AB", read());
	}
}
