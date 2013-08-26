package jocket.test;

import java.io.IOException;

import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

import org.junit.After;
import org.junit.Before;

public class AbstractJocketSocketTest {
  protected JocketSocket c;
  protected volatile JocketSocket s;
  protected ServerJocket srv;

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
}
