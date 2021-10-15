import java.util.HashSet;

public class Wget
{
    static final Object mutex_seen = new Object();

    public static void iterativeDownload(String initialURL)
    {
        final URLQueue queue = new ListQueue();
        final HashSet<String> seen = new HashSet<String>();

        // defines a new URLhandler
        DocumentProcessing.handler = new DocumentProcessing.URLhandler()
        {
            // this method will be called for each matched url
            @Override
            public void takeUrl(String url)
            {
                if (!seen.contains(url))
                {
                    queue.enqueue(url);
                    seen.add(url);
                }
            }
        };

        // to start, we push the initial url into the queue
        DocumentProcessing.handler.takeUrl(initialURL);
        while (!queue.isEmpty())
        {
            String url = queue.dequeue();
            Xurl.download(url);
        }
    }

    public static void multiThreadedDownload(String initialURL)
    {
        final URLQueue queue = new SynchronizedListQueue();
        final HashSet<String> seen = new HashSet<String>();

        // defines a new URLhandler
        DocumentProcessing.handler = new DocumentProcessing.URLhandler()
        {
            // this method will be called for each matched url
            @Override
            public void takeUrl(String url)
            {
                synchronized (mutex_seen)
                {
                    if (seen.add(url))
                        queue.enqueue(url);
                }
            }
        };

        // to start, we push the initial url into the queue
        DocumentProcessing.handler.takeUrl(initialURL);
        int start_thread_cnt = Thread.activeCount();
        while (true)
        {
            if (!queue.isEmpty())
            {
                String url = queue.dequeue();
                Thread thread = new Thread(new XurlThread(url));
                thread.start();
            }
            else if (Thread.activeCount() - start_thread_cnt <= 0)
                break;
        }
    }

    @SuppressWarnings("unused")
    public static void threadPoolDownload(int poolSize, String initialURL)
    {
        // to be completed later
    }

    public static void main(String[] args)
    {
        if (args.length < 1)
        {
          System.err.println("Usage: java Wget url");
          System.exit(-1);
        }
//        iterativeDownload(args[0]);
        multiThreadedDownload(args[0]);
    }
}

class XurlThread implements Runnable
{
    String url;

    public XurlThread(String url)
    {
        this.url = url;
    }

    @Override
    public void run()
    {
        Xurl.download(url);
    }
}
