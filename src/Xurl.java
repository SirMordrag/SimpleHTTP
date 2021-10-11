import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Xurl
{
    static boolean DEBUG = false;

    // url stuff
    String req_url;
    String req_protocol;
    String req_host;
    int req_port;
    String req_path;

    // file stuff
    File output_file;
    String req_filename;
    String req_file;
    List<String> req_header;
    BufferedWriter file_writer;
    int content_length;

    // connection stuff
    Socket xurl_socket;
    PrintStream xurl_writer;
    BufferedReader xurl_reader;
    boolean conn_open;
    Err_code server_response;

    private enum Err_code {
        INFO, OK, REDIRECT, CLIENT_ERR, SERVER_ERR
    }

    public Xurl(String url)
    {
        // set the line separator to be in line with http
        System.setProperty("line.separator","\r\n");
        conn_open = false;

        // DEBUG!
        // I have used https://www.tutorialspoint.com/javaexamples/net_webpage.htm as a reference and
        // general source of inspiration, however, no code was carried over
//        url = "http://www.tutorialspoint.com/javaexamples/net_singleuser.htm";
//        url = "http://www.enseignement.polytechnique.fr/";
//        url = "http://info.cern.ch/";
//        url = "http://www.enseignement.polytechnique.fr/profs/informatique/Philippe.Chassignet/test.html";
//        url = "http://www.andor.cz/";

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
        if(args.length < 1 || args.length > 3)
            error("No arguments given");

        new Xurl(args[0]);
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

    void openConnection(){
        try
        {
            xurl_socket = new Socket(req_host, req_port);
            debug("Connected to " + req_host + ":" + req_port);
        }
        catch (IOException e)
        {
            error("Failed to open connection at " + req_host + ":" + req_port, e.getMessage());
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
                if (content_length == 0) // no content available
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
                            error("Content reading interrupted immediately");
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
                    if (split_string[0].equals("Content-Length"))
                        try {
                            content_length = Integer.parseInt((split_string[1].trim()));
                        } catch (NumberFormatException e) {
                            error("Invalid Content-Length in response", e.getMessage());
                        }
            }
            debug("Received cont-len: " + content_length);
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
        xurl_writer.println(keyword.toUpperCase() + " " + req_path + " " + req_protocol.toUpperCase() + "/1.1");
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
        System.err.println("Error:" + err_msg);
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

