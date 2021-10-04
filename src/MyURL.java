import javax.swing.*;
import java.net.*;
import java.io.*;

public class MyURL
{
    String url_full;
    String url_protocol;
    String url_host;
    int url_port;
    String url_path;

    public MyURL(String url) throws IllegalArgumentException {
        // pass url to functions
        url_full = url;
        url_protocol = getProtocol();
        url_host = getHost();
        url_port = getPort();
        url_path = getPath();
        checkURLCompleteness();
    }

    public static void main(String[] args) throws IllegalArgumentException
    {
        if(args.length != 1)
            throw new IllegalArgumentException("Missing argument!");

        MyURL Url_parser = new MyURL(args[0]);

//        System.out.println(Url_parser.url_full);
//        System.out.println(Url_parser.url_protocol);
//        System.out.println(Url_parser.url_host);
//        System.out.println(Url_parser.url_port);
//        System.out.println(Url_parser.url_path);
    }

    String getProtocol() throws IllegalArgumentException
    {
        String url = url_full;
        String protocol;
        // split url
        String[] split_string = url.split("://");
        // we're only interested in the first part
        if (split_string.length > 0)
            protocol = split_string[0];
        else
            throw new IllegalArgumentException("Invalid control character placement");

        // run checks
        if (protocol.isEmpty() || split_string.length != 2)
            throw new IllegalArgumentException("No protocol or wrong syntax");

        return protocol;
    }

    String getHost() throws IllegalArgumentException
    {
        String url = url_full;
        // split url to isolate Protocol
        String[] split_string = url.split("://");
        // split again to isolate host & port
        split_string = split_string[1].split("/");
        // check if we have port
        if (split_string[0].contains(":"))
            // split again to isolate port
            split_string = split_string[0].split(":");
        // we're only interested in the first part
        String host = split_string[0];

        // run checks
        if (host.isEmpty())
            throw new IllegalArgumentException("Missing protocol");

        return host;
    }

    int getPort() throws IllegalArgumentException {
        int port;
        String url = url_full;
        // split url to isolate Protocol
        String[] split_string = url.split("://");
        // split again to isolate host & port
        split_string = split_string[1].split("/");
        // check if we have port
        if (split_string[0].contains(":")) // there is a port
        {
            // split again to isolate port
            split_string = split_string[0].split(":");
            if (split_string.length < 2)
                throw new IllegalArgumentException("Invalid control character placement");
            // try to convert it into an integer
            try{
                port = Integer.parseInt(split_string[1]);
            }
            catch (NumberFormatException e){
                throw new IllegalArgumentException("Non-numeric value in Port field");
            }

            if (port < 0)
                throw new IllegalArgumentException("Negative Port");
        }
        else // no port
        {
            port = -1;
        }

        return port;
    }

    String getPath() throws IllegalArgumentException
    {
        String url = url_full;
        // split url to isolate Protocol
        String[] split_string = url.split("://");
        // if we don't have at least one '/', we're in trouble
        if (!split_string[1].contains("/"))
            throw new IllegalArgumentException("Host (or port) is not terminated by '/'");
        // split again to isolate path, but only once, to ensure the path remains intact
        split_string = split_string[1].split("/", 2);
        // put it into a variable and add the first '/', which was lost during splitting
        String path = "/" + split_string[1];

        return path;
    }

    void checkURLCompleteness() throws IllegalArgumentException
    {
        String port = "";
        if(url_port != -1)
            port = ":" + String.valueOf(url_port);

        String extracted_url = url_protocol + "://" + url_host + port + url_path;

        if(!extracted_url.equals(url_full))
            throw new IllegalArgumentException("Extracted URL does not much input: check control characters");
    }
}

//// Class to handle errors
//// Taken from Chapter 1 of "A Primer on: Exceptions and Exception Handling in Java"
//// by Thomas Heide Clausen, Philippe Chassignet
//class UrlError extends Exception
//{
//    UrlError(String msg)
//    {
//        super("Invalid URL format: " + msg);
//    }
//}