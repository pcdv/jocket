package jocket.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ReadLoop {

	public ReadLoop(InputStream reader, OutputStream sink) throws IOException {
		byte[] buf = new byte[1024];
		while (true) {
			int len = reader.read(buf);
			if (len >= 0) {
				sink.write(buf, 0, len);
				sink.flush();
			} else {
				System.out.println("Closed");
				break;
			}
		}
	}
}
