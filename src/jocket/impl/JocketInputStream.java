package jocket.impl;

import java.io.IOException;
import java.io.InputStream;

import jocket.wait.BusyYieldSleep;
import jocket.wait.WaitStrategy;

public class JocketInputStream extends InputStream {

	private final JocketReader reader;
	private final WaitStrategy wait;

	public JocketInputStream(JocketReader reader) {
		this(reader, new BusyYieldSleep());
	}

	public JocketInputStream(JocketReader reader, WaitStrategy wait) {
		this.reader = reader;
		this.wait = wait;
	}

	@Override
	public int read() throws IOException {
		byte[] buf = new byte[1];
		int len;
		do {
			len = read(buf);
			if (len < 0)
				return len;
		} while (len == 0);
		return buf[0];
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read;
		do {
			read = reader.read(b, off, len);
			if (read != 0)
				return read;
			wait.pause();
		} while (read == 0);
		return -1;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}

}
