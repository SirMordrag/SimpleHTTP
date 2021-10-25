import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Xserver
{
    static boolean DEBUG = true;

    int timeout = 4000;
    int strike_cnt = 0;
    ServerSocket xsrv_socket;
    ArrayList<Thread> thread_list;
    BlockingQueue<Socket> client_queue;

    public Xserver(int port, String root, int pool_size)
    {
        // establish a socket
        try {
            xsrv_socket = new ServerSocket(port);
            xsrv_socket.setSoTimeout(timeout);
        } catch (IOException e) {
            error("Failed to open Server on port " + port, e.getMessage());
        }

        // start threads
        thread_list = new ArrayList<>();
        for (int i = 0; i < pool_size; i++)
        {
            Thread thread = new Thread(new XserverThread(client_queue));
            thread.start();
            thread_list.add(thread);
        }

        // main loop
        while(strike_cnt < 5)
        {
            try
            {
//                Socket client_socket = xsrv_socket.accept();
                handleConnection(xsrv_socket.accept());
            } catch (IOException e) {
                error("Failed to listen to connection", e.getMessage());
            }

            if (isFinished())
                break;
            sleep(20);
        }

        // end threads
        for (Thread thread : thread_list)
                thread.interrupt();

        try {
            xsrv_socket.close();
        } catch (IOException e) {
            error("Failed to close server", e.getMessage());
        }
        sleep(500);
    }

    void handleConnection(Socket socket)
    {
        try
        {
            client_queue.put(socket);
        } catch (InterruptedException e)
        {
            error("Program interrupted", e.getMessage());
        }
    }

    boolean isFinished()
    {
        if(DEBUG)
            return false;

        int blocked_cnt = 0;
        for (Thread thread : thread_list)
            if (thread.getState() == Thread.State.WAITING)
                blocked_cnt++;
        if (blocked_cnt == thread_list.size())
            strike_cnt++;
        else
            strike_cnt = 0;

        return strike_cnt > 5;
    }

    public static void main(String[] args)
    {
        if (args.length == 2) // no proxy
            try{
                new Xserver(Integer.parseInt(args[0]), args[1], 1);
            } catch (NumberFormatException e) {error("Invalid argument", e.getMessage());}
        else if (args.length == 3) // proxy & port
            try{
                new Xserver(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {error("Invalid argument", e.getMessage());}
        else
            error("Invalid number (" + args.length + ") of arguments given");
    }

    //
    // MACROS
    //

    // sleep in ms
    static void sleep(int milliseconds)
    {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);} catch (InterruptedException ignored){}
    }

    // methods for handling errors, because I'm lazy to type it out each time
    static void error(String err_msg)
    {
        System.err.println("Error: " + err_msg);
        System.exit(1);
    }

    // overriding for the case of caught exception
    static void error(String err_msg, String excep_msg)
    {
        System.err.println("Error: " + err_msg);
        System.err.println("This error was caused by the following exception:");
        System.err.println(excep_msg);
        System.exit(1);
    }

    // debug method
    static void debug(String msg)
    {
        debug(msg, true);
    }

    static void debug(String msg, boolean ln)
    {
        if (DEBUG)
            if (ln)
                System.out.println(msg);
            else
                System.out.print(msg);
    }
}

class XserverThread implements Runnable
{
    BlockingQueue<Socket> queue;

    public XserverThread(BlockingQueue<Socket> queue)
    {
        this.queue = queue;
    }

    @Override
    public void run()
    {
        while(true)
        {
            try
            {
                Socket socket = queue.take();
                new XserverProcessor(socket);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}


class XserverProcessor
{
    Socket socket;
    PrintStream writer;
    BufferedReader reader;
    BufferedReader file_reader;
    List<String> req_header;
    String path;
    String file;

    public XserverProcessor(Socket client_socket)
    {
        socket = client_socket;

        openConnection();

        while(processRequest())
        {
            processRequest();
            sleep(100);
            if (socket.isClosed())
                break;
        }

        closeConnection();
    }

    boolean processRequest()
    {
        boolean response_ok = true;
        readHeader();
        if (!analyzeHeader())
        {
            path = ":tag:bad_request";
            response_ok = false;
        }

        getFile();
        writeResponse(response_ok);

        return response_ok;
    }

    void readHeader()
    {
        req_header = new ArrayList<>();
        try
        {
            for (int i = 0; ; i++)
            {
                String line = reader.readLine();
                debug(line);
                req_header.add(line + "\r\n");

                if(line.equals(""))
                    break;
            }
        } catch (IOException e)
        {
            error("Failed to read page", e.getMessage());
        }
    }

    boolean analyzeHeader()
    {
        if (req_header.size() < 1)
            return false;

        // http request
        String[] split_string = req_header.get(0).split(" ");
        if (split_string.length < 3)
            return false;
        if (!Objects.equals(split_string[0], "GET") || !Objects.equals(split_string[2], "HTTP/1.1"))
            return false;

        // path
        path = split_string[2];

        // host
        if (!req_header.get(1).startsWith("Host:"))
            return false;

        return true;
    }

    void getFile()
    {
        if (path.equals(":tag:bad_request"))
        {
            file = message404();
            return;
        }
        else if (path.equals("/"))
        {
            file = messageIndex();
            return;
        }

        try
        {
            File req_file = new File(path);
            file_reader = new BufferedReader(new FileReader(req_file));

            String line;
            file = "";
            while ((line = reader.readLine()) != null)
                file += line;

        } catch (IOException e) {
            error("Failed to read file", e.getMessage());
        }
    }

    void writeResponse(boolean response_ok)
    {
        if (response_ok)
            writer.println("HTTP/1.1 200 OK");
        else
            writer.println("HTTP/1.1 400 Bad Request");

        writer.println("Content-Type: radioactive");
        writer.println("Content-Length: " + file.length());
        writer.println();
        writer.println(file);
    }

    void openConnection()
    {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintStream(socket.getOutputStream());
        } catch (IOException e)
        {
            error("Failed to set up buffered reader/writer", e.getMessage());
        }
    }

    void closeConnection()
    {
        try {
            socket.close();
            reader.close();
            writer.close();
            file_reader.close();
        } catch (IOException e) {
            error("Failed to close connection", e.getMessage());
        }
    }

    //
    // MACROS
    //

    static String message404()
    {
        return "Sorry, bad request.";
    }

    static String messageIndex()
    {
        return "Welcome to my not-page!";
    }

    // sleep in ms
    static void sleep(int milliseconds)
    {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);} catch (InterruptedException ignored){}
    }

    // methods for handling errors, because I'm lazy to type it out each time
    static void error(String err_msg)
    {
        System.err.println("Error: " + err_msg);
        System.exit(1);
    }

    // overriding for the case of caught exception
    static void error(String err_msg, String excep_msg)
    {
        System.err.println("Error: " + err_msg);
        System.err.println("This error was caused by the following exception:");
        System.err.println(excep_msg);
        System.exit(1);
    }

    // debug method
    static void debug(String msg)
    {
        debug(msg, true);
    }

    static void debug(String msg, boolean ln)
    {
        if (ln)
            System.out.println(msg);
        else
            System.out.print(msg);
    }
}


