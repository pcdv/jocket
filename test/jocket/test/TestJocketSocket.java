package jocket.test;

import static junit.framework.Assert.assertEquals;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;

import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestJocketSocket {
	private JocketSocket c;
	private volatile JocketSocket s;
	private ServerJocket srv;

	@Before
	public void setUp() throws Exception {
		srv = new ServerJocket(0);
		new Thread() {
			@Override
			public void run() {
				try {
					s = srv.accept();
				} catch (IOException e) {
				}
			}
		}.start();
		c = new JocketSocket(srv.getLocalPort());
		while (s == null)
			Thread.sleep(1);
	}

	@After
	public void tearDown() throws Exception {
		srv.close();
	}

	@Test
	public void testWriteRead() throws Exception {
		OutputStream out = c.getOutputStream();
		out.write("hello".getBytes());
		out.flush();
		byte[] buf = new byte[100];
		int len = s.getInputStream().read(buf);
		assertEquals(5, len);
		assertEquals("hello", new String(buf, 0, 5));
	}

	@Test
	public void testClose() throws Exception {
		c.getOutputStream().close();
		assertEquals(-1, s.getInputStream().read());
	}

	/**
	 * Used to reproduce a bug.
	 */
	@Test
	public void testUnderflow() throws Exception {
		byte[] buf = new byte[300000];
		for (int i = 0; i < 100; i++) {
			System.out.println(i);
			c.getOutputStream().write(buf);
			c.getWriter().flush();
			new DataInputStream(s.getInputStream()).readFully(buf);
		}
	}
}
