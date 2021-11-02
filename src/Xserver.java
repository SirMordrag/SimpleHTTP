// This exercise is completed by Weijun Huang and Voska Vojtech

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Xserver {
    static boolean DEBUG = true;

    int timeout = 40000;
    int strike_cnt = 0;
    ServerSocket xsrv_socket;
    ArrayList<Thread> thread_list;
    BlockingQueue<Socket> client_queue;

    public Xserver(int port, String root, int pool_size) {
        client_queue = new LinkedBlockingQueue<>();
        try {
            xsrv_socket = new ServerSocket(port);
            xsrv_socket.setSoTimeout(timeout);
        } catch (IOException e) {
            error("Failed to open Server on port " + port, e.getMessage());
            System.exit(-1);
        }

        // start threads
        thread_list = new ArrayList<>();
        for (int i = 0; i < pool_size; i++)
        {
            Thread thread = new Thread(new XserverThread(client_queue, root));
            thread.start();
            thread_list.add(thread);
        }

        while (true)
        {
            try
            {
                Socket client_socket = xsrv_socket.accept();
                if (client_socket != null)
                    handleConnection(client_socket);
            } catch (SocketTimeoutException e) {
                break;
            } catch (IOException e) {
                error("Failed to listen to connection", e.getMessage());
            }
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

    void handleConnection(Socket socket) {
        try {
            client_queue.put(socket);
        } catch (InterruptedException e) {
            error("Program interrupted", e.getMessage());
        }
    }

    boolean isFinished() {
//        if (DEBUG)
//            return false;

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

    public static void main(String[] args) {
        if (args.length == 2) // no proxy
            try {
                new Xserver(Integer.parseInt(args[0]), args[1], 1);
            } catch (NumberFormatException e) {
                error("Invalid argument", e.getMessage());
            }
        else if (args.length == 3) // proxy & port
            try {
                new Xserver(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                error("Invalid argument", e.getMessage());
            }
        else
            error("Invalid number (" + args.length + ") of arguments given");
    }

    //
    // MACROS
    //

    // sleep in ms
    static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException ignored) {
        }
    }

    // methods for handling errors, because I'm lazy to type it out each time
    static void error(String err_msg) {
        System.err.println("Error: " + err_msg);
        System.exit(1);
    }

    // overriding for the case of caught exception
    static void error(String err_msg, String excep_msg) {
        System.err.println("Error: " + err_msg);
        System.err.println("This error was caused by the following exception:");
        System.err.println(excep_msg);
        System.exit(1);
    }

    // debug method
    static void debug(String msg) {
        debug(msg, true);
    }

    static void debug(String msg, boolean ln) {
        if (DEBUG)
            if (ln)
                System.out.println(msg);
            else
                System.out.print(msg);
    }
}

class XserverThread implements Runnable {
    BlockingQueue<Socket> queue;
    String root;

    public XserverThread(BlockingQueue<Socket> queue, String root)
    {
        this.queue = queue;
        this.root = root;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Socket socket = queue.take();
                new XserverProcessor(socket, root);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}


class XserverProcessor {
    Socket socket;
    PrintStream writer;
    BufferedReader reader;
    BufferedReader file_reader;
    List<String> req_header;
    String path;
    String file;
    int response_ok = 200;
    Boolean file_exist;
    String root_dir;


    public XserverProcessor(Socket client_socket, String root) {
        socket = client_socket;
        root_dir = root;

        openConnection();

        do
        {
            processRequest();
            sleep(100);
        }
        while (!socket.isClosed() && (response_ok == 200 || response_ok == 404));

        closeConnection();
    }

    void processRequest() {
        file_exist = true;
        response_ok = 200;
        if (!readHeader())
        {
            response_ok = 600; // connection closed
            return;
        }
        if (!analyzeHeader()) {
            path = ":tag:bad_request";
            response_ok = 400;
            debug("BAD REQUEST");
        }
        getFile();

        writeResponse(response_ok);
    }


    boolean readHeader() {
        req_header = new ArrayList<>();
        try {
            for (int i = 0; ; i++) {
                String line = reader.readLine();
                debug(line);
                req_header.add(line + "\r\n");

                if (line == null)
                    return false;
                if (line.equals(""))
                    break;
            }
        } catch (IOException e) {
            error("Failed to read header", e.getMessage());
        }
        return true;
    }

    boolean analyzeHeader() {
        Pattern r = Pattern.compile("(GET) (/[^ ]*) (HTTP/1.1)$");
        Matcher m = r.matcher(req_header.get(0));

        debug("HERE IS HEADER");
        // http request
        if (!m.find()) {
            return false;
        }
        debug("HERE IS HEADER");
        // path
        path = m.group(2);

        // host
        r = Pattern.compile("^(Host:).+");
        m = r.matcher(req_header.get(1));
        if (!m.find()) return false;

        return true;
    }

    boolean getFile() {
        debug("Path: " + path);
        if (path.equals(":tag:bad_request"))
        {
            file = message400();
            response_ok = 400;
            return false;
        }
        else if (path.equals("/")) {
            file = messageIndex();
            return true;
        }
        else
        {
            path = root_dir + path;
        }
        debug("Full Path: " + path);

        try {
//            File req_file = new File(path);
//            file_reader = new BufferedReader(new FileReader(path));
//
//            String line;
//            file = "";
//            while (file_reader.ready())
//            {
//                line = file_reader.readLine();
//                debug(line);
//                file += (line + "\r\n");
//            }
            file = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);

        } catch (IOException e)
        {
            file = message404();
            response_ok = 404;
        }
        return true;
    }

    void writeResponse(int response_ok) {
        if (response_ok == 200) {
            writer.println("HTTP/1.1 200 OK");

        } else if (response_ok == 400){
            writer.println("HTTP/1.1 400 Bad Request");
        } else if(response_ok == 404) {
            writer.println("HTTP/1.1 404 Not Found");
        }
//        writer.println("Content-Type: radioactive");
        writer.println("Content-Length: " + file.length());
        writer.println();
        writer.print(file);
//        writer.print("\r\n");
        writer.flush();
    }

    void openConnection() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintStream(socket.getOutputStream());
        } catch (IOException e) {
            error("Failed to set up buffered reader/writer", e.getMessage());
        }
    }

    void closeConnection() {
        try {
            socket.close();
            reader.close();
            writer.close();
            if (file_reader != null)
                file_reader.close();
            debug("Connection Closed");
        } catch (IOException e) {
            error("Failed to close connection", e.getMessage());
        }
    }

    //
    // MACROS
    //

    static String message400() {
        return "Sorry, bad request.\r\n";
    }

    static String message404() {
        return "Sorry, not found.\r\n";
    }

    static String messageIndex() {
        return "Welcome to my not-page!\r\n";
    }

    // sleep in ms
    static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException ignored) {
        }
    }

    // methods for handling errors, because I'm lazy to type it out each time
    static void error(String err_msg) {
        System.err.println("Error: " + err_msg);
        System.exit(1);
    }

    // overriding for the case of caught exception
    static void error(String err_msg, String excep_msg) {
        System.err.println("Error: " + err_msg);
        System.err.println("This error was caused by the following exception:");
        System.err.println(excep_msg);
        System.exit(1);
    }

    // debug method
    static void debug(String msg) {
        debug(msg, true);
    }

    static void debug(String msg, boolean ln) {
        if (ln)
            System.out.println(msg);
        else
            System.out.print(msg);
    }
}


