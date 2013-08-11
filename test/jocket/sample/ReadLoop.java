package jocket.sample;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.LockSupport;

import jocket.impl.JocketReader;

public class ReadLoop {

	public ReadLoop(JocketReader reader, OutputStream sink) throws IOException {
		byte[] buf = new byte[1024];
		while (true) {
			int len = reader.read(buf);
			if (len > 0) {
				sink.write(buf, 0, len);
				sink.flush();
			} else
				LockSupport.parkNanos(1000000);
		}
	}
}
