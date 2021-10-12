import java.util.HashSet;

public class Wget {

  public static void iterativeDownload(String initialURL) {
    final URLQueue queue = new ListQueue();
    final HashSet<String> seen = new HashSet<String>();
    // defines a new URLhandler
    DocumentProcessing.handler = new DocumentProcessing.URLhandler() {
      // this method will be called for each matched url
      @Override
      public void takeUrl(String url) {
        // to be completed at exercise 2
      }
    };
    // to start, we push the initial url into the queue
    DocumentProcessing.handler.takeUrl(initialURL);
    while (!queue.isEmpty()) {
      String url = queue.dequeue();
      Xurl.download(url); // don't change this line
    }
  }

  @SuppressWarnings("unused")
  public static void multiThreadedDownload(String initialURL) {
    // to be completed later
  }

  @SuppressWarnings("unused")
  public static void threadPoolDownload(int poolSize, String initialURL) {
    // to be completed later
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: java Wget url");
      System.exit(-1);
    }
    iterativeDownload(args[0]);
  }

}
