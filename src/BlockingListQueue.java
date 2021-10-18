import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * Basic implementation with a LinkedList.
 */
public class BlockingListQueue implements URLQueue {

    private final LinkedList<String> queue;

    public BlockingListQueue()
    {
        this.queue = new LinkedList<String>();
    }

    @Override
    public synchronized boolean isEmpty()
    {
        return this.queue.size() == 0;
    }

    @Override
    public synchronized boolean isFull() {
        return false;
    }

    @Override
    public synchronized void enqueue(String url)
    {
        this.queue.add(url);
        notifyAll();
    }

    @Override
    public synchronized String dequeue()
    {
        while (isEmpty())
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "**STOP**";
            }

        String retval = this.queue.remove();
        if (retval.equals("**STOP**"))
            Thread.currentThread().interrupt();
        return retval;
    }

}
