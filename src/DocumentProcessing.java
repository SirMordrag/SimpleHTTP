import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentProcessing
{
    static boolean DEBUG = false;

    public static void main(String[] args)
    {
        if (DEBUG)
        {
            parseBuffer("≺a\nHref='http://name/file.ext'≻");
            parseBuffer("arseBuffer_should_not_match_any_url_in_|≺ahref='http://Any/any'≻");
            parseBuffer("Failure:_parseBuffer_should_not_match_any_url_in_|≺a hhref=\"http://na.me/File\"≻|]");
            parseBuffer("≺a href=\"http://n2VALIDa.me/File\"≻|]");
            parseBuffer("≺a fdfdf href=\"http://n3VALIDa.me/File\"≻|]");
            parseBuffer("≺afdfdf href=\"http://n4a.me/File\"≻|]");
            parseBuffer("≺a href=\"http://n5a.me/File''≻|]");
            parseBuffer("≺a fdfdf href='http://n6a.me/File\"≻|]");
            parseBuffer("≺a_href='http://nameVALID/file.ext'≻");
            parseBuffer("match_any_url_in_|≺a href=\"http://name/File'≻XY≺/a≻ ≺a href='http://name/File\"≻|]");
        }
        else
            parseBuffer(args[0]);
    }

    public interface URLhandler
    {
        void takeUrl(String url);
    }

    public static URLhandler handler = new URLhandler()
    {
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
    public static void parseBuffer(CharSequence data)
    {
        debug("Working on: " + data.toString());

        // Create regular expression, make it into pattern (flagged case insensitive), and then match it in data
        // <A <space> <arbitrary> <space if <arbitrary> exists> href <maybe space> = <maybe space> <" or '> some_url <" or '> <arbitrary> >
        String regex = "[<≺]A(\\s|\\s[\\s\\S]*?\\s)href\\s*=\\s*[\"'](.*?)[\"'][\\s\\S]*?[>≻]";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(data);

        String url;
        while (matcher.find())
        {
            // extract URL
            url = matcher.group(2);

            // check apostrophes
            if (!((data.charAt(matcher.start(2) - 1) == data.charAt(matcher.end(2))) &&
                 ((data.charAt(matcher.start(2) - 1) == '\'') ||
                  (data.charAt(matcher.start(2) - 1) == '\"'))))
                continue;

            if (url != null)
            {
                try {
                    // validate it in MyURL
                    MyURL url_check = new MyURL(url);
                    // check protocol
                    if (!url_check.getProtocol().equalsIgnoreCase("http"))
                        throw new IllegalArgumentException();
                    // pass it to handler
                    handler.takeUrl(url);
                    debug("Processed valid URL: " + url);

                } catch (IllegalArgumentException e) {
                    debug("Scrapped invalid URL: " + url);
                }
            }
        }
    }

    // debug method
    static void debug(String msg)
    {
        if (DEBUG)
            System.out.println(msg);
    }
}
