package jocket.bench;

public interface Settings {

  int REPS = Integer.getInteger("reps", 300000);

  int REPLY_SIZE = Integer.getInteger("replySize", 1024);

  int BATCH = Integer.getInteger("batch", 1);

  boolean USE_JOCKET = !Boolean.getBoolean("tcp");

  int PORT = Integer.getInteger("port", 3333);

  int WARMUP = Integer.getInteger("warmup", 50000);

  long PAUSE = Long.getLong("pause", 0);

  String OUTPUT_FILE = USE_JOCKET ? "Jocket" : "Socket";

  boolean NOSTATS = Boolean.getBoolean("nostats");
}
