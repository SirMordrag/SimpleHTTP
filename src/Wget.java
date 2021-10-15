import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class Wget
{
    static final Object mutex_seen = new Object();
    static boolean use_in_band_signaling = true;

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
                if (seen.add(url))
                    queue.enqueue(url);
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

    public static void threadPoolDownload(int poolSize, String initialURL)
    {
        final URLQueue queue = new BlockingListQueue();
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
        ArrayList<Thread> thread_list = new ArrayList<>();

        // start threads
        for (int i = 0; i < poolSize; i++)
        {
            Thread thread = new Thread(new XurlPoolThread(queue));
            thread.start();
            thread_list.add(thread);
        }

        // wait for finish
        int strike_cnt = 0;
        while(strike_cnt < 5)
        {
            int blocked_cnt = 0;
            for (Thread thread : thread_list)
                if (thread.getState() == Thread.State.WAITING)
                    blocked_cnt++;
            if (blocked_cnt == poolSize)
                strike_cnt++;
            else
                strike_cnt = 0;
            sleep(20);
        }

        // end threads
        for (Thread thread : thread_list)
            if (use_in_band_signaling)
                thread.interrupt();
            else
                queue.enqueue("**STOP**");

        sleep(500);
    }

    public static void main(String[] args)
    {
        if (args.length < 1)
        {
          System.err.println("Usage: java Wget url");
          System.exit(-1);
        }
        
//        iterativeDownload(args[0]);
//        multiThreadedDownload(args[0]);
        threadPoolDownload(Integer.parseInt(args[0]), args[1]);
    }

    static void sleep(int milliseconds)
    {
        try {TimeUnit.MILLISECONDS.sleep(milliseconds);} catch (InterruptedException ignored){}
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

class XurlPoolThread implements Runnable
{
    URLQueue queue;

    public XurlPoolThread(URLQueue queue)
    {
        this.queue = queue;
    }

    @Override
    public void run()
    {
        while(true)
        {
            String url = queue.dequeue();
            if (url.equals("**STOP**") || Thread.interrupted())
                break;
            Xurl.download(url);
        }
    }
}
