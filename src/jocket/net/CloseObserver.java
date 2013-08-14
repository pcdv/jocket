package jocket.net;

import java.util.Observable;
import java.util.Observer;

import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class CloseObserver implements Observer {

	private final JocketReader reader;
	private final JocketWriter writer;

	public CloseObserver(JocketReader reader, JocketWriter writer) {
		this.reader = reader;
		this.writer = writer;
		reader.addCloseListener(this);
		writer.addCloseListener(this);
	}

	@Override
	public void update(Observable o, Object arg) {
		reader.close();
		writer.close();
	}
}
