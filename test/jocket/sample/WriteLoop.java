package jocket.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.LockSupport;

import jocket.impl.JocketWriter;

public class WriteLoop {

	public WriteLoop(JocketWriter writer, InputStream in) throws IOException {
		byte[] buf = new byte[1024];
		while (true) {
			final int read = in.read(buf);
			if (read == -1) {
				System.out.println("Closing...");
				writer.close();
				return;
			}
			int written = 0;
			while (written < read) {
				int len = writer.write(buf, written, read - written);
				if (len > 0) {
					written += len;
					writer.flush();
				} else
					LockSupport.parkNanos(1000000);
			}
		}
	}
}
