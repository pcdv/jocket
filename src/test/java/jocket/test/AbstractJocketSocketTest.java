package jocket.test;

import java.io.IOException;

import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractJocketSocketTest {
  protected JocketSocket c;
  protected volatile JocketSocket s;
  protected ServerJocket srv;

  @BeforeEach
  public void setUp() throws Exception {
    srv = new ServerJocket(0);
    new Thread() {
      @Override
      public void run() {
        try {
          s = srv.accept();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }.start();
    c = new JocketSocket(srv.getLocalPort());
    while (s == null)
      Thread.sleep(1);
  }

  @AfterEach
  public void tearDown() throws Exception {
    srv.close();
  }
}
