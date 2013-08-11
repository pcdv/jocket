package jocket.impl;

import java.util.concurrent.locks.LockSupport;

public class ReadLoop {

	public ReadLoop(JocketReader reader) {
		byte[] buf = new byte[1024];
		while (true) {
			int len = reader.read(buf);
			if (len > 0) {
				System.out.println(new String(buf, 0, len));
			} else
				LockSupport.parkNanos(1000000);
		}
	}

}
