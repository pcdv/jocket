package jocket.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.LockSupport;

public class WriteLoop {

	public WriteLoop(JocketWriter writer, InputStream in) throws IOException {
		byte[] buf = new byte[1024];
		while (true) {
			int read = in.read(buf);
			int written = 0;
			while (written < read) {
				int len = writer.write(buf, written, read - written);
				if (len == 0)
					LockSupport.parkNanos(1000000);
				else {
					written += len;
					writer.flush();
					System.out.println("written " + len);
				}
			}
		}
	}
}
