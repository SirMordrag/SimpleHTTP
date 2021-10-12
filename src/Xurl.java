import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

public class Xurl
{
    static boolean DEBUG = false;

    // url stuff
    String req_url;
    String req_protocol;
    String req_host;
    int req_port;
    String req_path;
    int timeout = 2000;

    // proxy stuff
    boolean use_proxy;
    String proxy_name;
    int proxy_port;

    // file stuff
    File output_file;
    String req_filename;
    String req_file;
    List<String> req_header;
    BufferedWriter file_writer;
    int content_length;
    boolean chunked_encoding;

    // connection stuff
    Socket xurl_socket;
    PrintStream xurl_writer;
    BufferedReader xurl_reader;
    boolean conn_open = false;
    Err_code server_response;

    private enum Err_code {
        INFO, OK, REDIRECT, CLIENT_ERR, SERVER_ERR
    }

    public Xurl(String url, String url_proxy, String url_proxy_port)
    {
        // I have used https://www.tutorialspoint.com/javaexamples/net_webpage.htm as a reference and
        // general source of inspiration, however, no code was carried over
        if (DEBUG)
        {
            timeout *= 4;
//            url = "http://www.tutorialspoint.com/javaexamples/net_singleuser.htm";
//        url = "http://www.enseignement.polytechnique.fr/";
//        url = "http://info.cern.ch/";
//        url = "http://www.enseignement.polytechnique.fr/profs/informatique/Philippe.Chassignet/test.html";
        url = "http://www.enseignement.polytechnique.fr/informatique/profs/Philippe.Chassignet/3A.html";
//        url_proxy = "103.150.89.154";
//        url_proxy_port = "8118";
        }

        // set the line separator to be in line with http
        System.setProperty("line.separator","\r\n");

        if (url_proxy != null)
        {
            try {
                use_proxy = true;
                proxy_name = url_proxy;
                proxy_port = Integer.parseInt(url_proxy_port);
            } catch (NumberFormatException e) {
                error("Invalid format of proxy port", e.getMessage());
            }
            if (proxy_port < 0)
                error("Negative proxy port");
        }

        // get the URL
        try {
            extractURL(url);
        } catch (IllegalArgumentException e) {
            error("Invalid URL given", e.getMessage());
        }

        // validate it and get filename
        validateURL();
        openConnection();
        do {downloadFile();} while (server_response == Err_code.REDIRECT);
        if(server_response == Err_code.OK)
            saveFile();
        else
            error("Could not retrieve file: " + req_header.get(0));
        closeConnection();
    }

    public static void main(String[] args)
    {
        if (args.length == 1) // no proxy
            new Xurl(args[0], null, null);
        else if (args.length == 3) // proxy & port
            new Xurl(args[0], args[1], args[2]);
        else
            error("Invalid number (" + args.length + ") of arguments given");
    }

    // extract all the parameters from the given url
    void extractURL(String url)
    {
        MyURL url_extractor = new MyURL(url);
        req_url = url;
        req_protocol = url_extractor.getProtocol();
        req_host = url_extractor.getHost();
        req_port = url_extractor.getPort();
        req_path = url_extractor.getPath();
    }

    // validate that the url is something we can use
    void validateURL()
    {
        // protocol must be http
        if (!Objects.equals(req_protocol, "http") && !Objects.equals(req_protocol, "https"))
            error("Invalid protocol, only http(s) is supported");

        // port must be valid, if no port provided, we use default 80
        if (req_port == -1)
            req_port = 80;

        // path is okay, but we need to extract the filename (index if there's none)
        if (req_path.endsWith("/"))
            req_filename = "index";
        else
        {
            String[] split_str = req_path.split("/");
            req_filename = split_str[split_str.length - 1];
        }
    }

    void openConnection()
    {
        String host;
        int port;
        if (use_proxy)
        {
            host = proxy_name;
            port = proxy_port;
        }
        else
        {
            host = req_host;
            port = req_port;
        }

        try
        {
            xurl_socket = new Socket();
            xurl_socket.connect(new InetSocketAddress(host, port), timeout); // connection timeout
            xurl_socket.setSoTimeout(timeout); // download timeout
            debug("Connected to " + host + ":" + port);
        }
        catch (IOException e)
        {
            error("Failed to open connection at " + host + ":" + port, e.getMessage());
        }
        conn_open = true;

        try {
            xurl_reader = new BufferedReader(new InputStreamReader(xurl_socket.getInputStream()));
            xurl_writer = new PrintStream(xurl_socket.getOutputStream());
        } catch (IOException e)
        {
            error("Failed to set up buffered reader/writer", e.getMessage());
        }

        if (xurl_reader == null || xurl_writer == null)
            error("Failed to initialize reader/writer");
    }

    void downloadFile()
    {
        String line;

        // send request
        sendHTTPRequest("GET");

        // download header
        req_header = new ArrayList<>();
        try
        {
            for (int i = 0; ; i++)
            {
                line = xurl_reader.readLine();
                debug(line);
                req_header.add(line + "\r\n");

                if(line.equals(""))
                    break;
            }
        } catch (IOException e)
        {
            error("Failed to read page", e.getMessage());
        }

        debug("Finished reading header");

        // analyze header
        analyzeHeader();

        // download redirect url in needed
        if(server_response == Err_code.REDIRECT)
            extractRedirectURL();

        // download file
        if (server_response == Err_code.OK)
        {
            req_file = "";

            try
            {
                if (chunked_encoding)
                {
                    int chunk_length = 0;
                    int buff;
                    char[] content;
                    req_file = "";

                    while(true)
                    {
                        // get chunk length
                        line = xurl_reader.readLine();
                        try {
                            chunk_length = Integer.parseInt(line.trim(), 16);
                        } catch (NumberFormatException e) {
                            error("Non-numeric chunk", e.getMessage());
                        }
                        if (chunk_length == 0)
                            break;

                        // get chunk content
                        content = new char[chunk_length];
                        for(int i = 0; i < chunk_length; i++)
                        {
                            buff = xurl_reader.read();
                            if (buff == -1)
                                error("Content reading interrupted too soon");
                            content[i] = (char) buff;
                        }

                        req_file += new String(content);

                        // skip CRLF
                        xurl_reader.read();
                        xurl_reader.read();
                    }
                    debug(req_file);
                    debug("file len: " + req_file.length());
                }
                else if (content_length == 0) // no content available
                    error("No page content provided");
                else if (content_length == -1) // no Content-Length
                {
                    debug("Downloading without CL");
                    while (xurl_reader.ready())
                    {
                        line = xurl_reader.readLine();
                        debug(line);
                        req_file += (line + "\r\n");
                    }
                }
                else // Content-Length available
                {
                    debug("Downloading using CL");
                    char[] content = new char[content_length];
                    int buff;
                    for(int i = 0; i < content_length; i++)
                    {
                        buff = xurl_reader.read();
                        if (buff == -1)
                            error("Content reading interrupted too soon");
                        content[i] = (char) buff;
                    }
                    req_file = new String(content);
                    debug(req_file);
                    debug("file len: " + req_file.length());
                }
            } catch (IOException e) {
                error("Failed to read page", e.getMessage());
            }
        }

    }

    void saveFile()
    {
        try
        {
            output_file = new File("./" + req_filename);
            file_writer = new BufferedWriter(new FileWriter(output_file));

            file_writer.write(req_file);

            file_writer.close();
        }
        catch (IOException e)
        {
            error("Failed to write file", e.getMessage());
        }
    }

    void closeConnection()
    {
        if (conn_open)
        {
            try {
                xurl_socket.close();
                xurl_writer.close();
                xurl_reader.close();
            } catch (IOException e) {
                error("Failed to close connection", e.getMessage());
            }
        }
        debug("Connection closed");
    }

    //
    // Helpers
    //

    void analyzeHeader()
    {
        String[] split_string;
        int response = 0;

        // analyze header
        if (req_header.size() >= 1)
        {
            // http response
            split_string = req_header.get(0).split(" ");
            if (split_string.length < 3)
                error("Invalid server response");
            if (!Objects.equals(split_string[0].split("/")[0], req_protocol.toUpperCase()))
                error("Invalid protocol in server response");

            // error code
            try
            {
                response = Integer.parseInt(split_string[1]);
                if (response < 100 || response >= 600)
                    error(" HTTP error code in response is out of bounds");
            } catch (NumberFormatException e)
            {
                error("Invalid HTTP error code in response", e.getMessage());
            }

            // content length
            content_length = -1;
            for(int i = 1; i < req_header.size(); i++)
            {
                split_string = req_header.get(i).split(":");
                if (split_string.length > 1)
                {
                    if (split_string[0].equals("Content-Length"))
                        try
                        {
                            content_length = Integer.parseInt((split_string[1].trim()));
                        } catch (NumberFormatException e)
                        {
                            error("Invalid Content-Length in response", e.getMessage());
                        }
                    if (split_string[0].equals("Transfer-Encoding"))
                    {
                        split_string[1] = split_string[1].trim();
                        if (split_string[1].trim().equalsIgnoreCase("chunked"))
                            chunked_encoding = true;
                    }
                }
            }
            debug("Received cont-len: " + content_length);
            debug("Chunked transfer encoding: " + chunked_encoding);
        }
        else
            error("Failed to read content from server (content is null)");

        // save err_code
        switch (response / 100)
        {
            case (1):
                server_response = Err_code.INFO;
                break;
            case (2):
                server_response = Err_code.OK;
                break;
            case (3):
                server_response = Err_code.REDIRECT;
                break;
            case (4):
                server_response = Err_code.CLIENT_ERR;
                break;
            case (5):
                server_response = Err_code.SERVER_ERR;
                break;
        }
    }

    void extractRedirectURL()
    {
        error("Redirection unsupported at this time");
    }

    void sendHTTPRequest(String keyword, String[] params)
    {
        String path;
        if (use_proxy)
        {
            if (req_port != 80)
                path = req_protocol + "://" + req_host + ":" + req_port + req_path;
            else
                path = req_protocol + "://" + req_host + req_path;
        }
        else
            path = req_path;

        xurl_writer.println(keyword.toUpperCase() + " " + path + " " + req_protocol.toUpperCase() + "/1.1");
        debug(keyword + " " + req_path + " " + req_protocol.toUpperCase() + "/1.1");
        if (req_port == 80)
            xurl_writer.println("Host: " + req_host);
        else
            xurl_writer.println("Host: " + req_host + ":" + req_port);
        debug("Host: " + req_host);
        if (params != null)
        {
            for(int i = 0; i < params.length; i++)
            {
                xurl_writer.println(params[i]);
                debug(params[i]);
            }
        }
        xurl_writer.println("");
        xurl_writer.println("");
    }

    // overriding for simplicity
    void sendHTTPRequest(String keyword)
    {
        sendHTTPRequest(keyword, null);
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

