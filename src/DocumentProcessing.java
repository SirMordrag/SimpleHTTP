public class DocumentProcessing {

  public interface URLhandler {
    void takeUrl(String url);
  }

  public static URLhandler handler = new URLhandler() {
    @Override
    public void takeUrl(String url) {
      System.out.println(url);        // DON'T change anything here
    }
  };

  /**
   * Parse the given buffer to fetch embedded links and call the handler to
   * process these links.
   * 
   * @param data
   *          the buffer containing the html document
   */
  public static void parseBuffer(CharSequence data) {
    // TODO at exercise 1
    // call handler.takeUrl for each matched url
  }

}
