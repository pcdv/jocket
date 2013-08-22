package jocket.impl;

import java.io.IOException;
import java.io.OutputStream;

import jocket.wait.BusyYieldSleep;
import jocket.wait.WaitStrategy;

public class JocketOutputStream extends OutputStream {

	private final JocketWriter writer;
	private final WaitStrategy wait;

	public JocketOutputStream(JocketWriter writer, WaitStrategy wait) {
		this.writer = writer;
		this.wait = wait;
	}

	public JocketOutputStream(JocketWriter writer) {
		this(writer, new BusyYieldSleep());
	}

	@Override
	public void write(int b) throws IOException {
		write(new byte[] { (byte) b }, 0, 1);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			len -= writer.write(b, off, len);
			if (len > 0)
				wait.pause();
		}
		wait.reset();
	}

	@Override
	public void flush() throws IOException {
		writer.flush();
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}
}
