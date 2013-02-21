package no.java.ems.wiki;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class DefaultWikiEngine<S extends WikiSink> implements WikiEngine {

    private BufferedReader reader;

    private String currentLine;

    private S sink;

    public DefaultWikiEngine(S sink) {
        this.sink = sink;
    }

    // -----------------------------------------------------------------------
    // WikiEngine
    // -----------------------------------------------------------------------

    public void transform(String text) throws IOException {
        reader = new BufferedReader(new StringReader(text));

        sink.startDocument();

        while (getLine()) {
            if (currentLine.startsWith("h1. ")) {
                sink.onHeading1(currentLine.substring(4));
                continue;
            }

            if (currentLine.equals("")) {
                continue;
            }

            if (currentLine.startsWith("*")) {
                doUnorderedList(0);
            }
            else {
                onParagraph();
            }
        }

        sink.endDocument();
    }

    public S getSink() {
        return sink;
    }

    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    private void onParagraph() throws IOException {
        sink.startParagraph();

        do {
            if (currentLine.equals("")) {
                break;
            }

            sink.onText(currentLine);
        } while (getLine());

        sink.endParagraph();
    }

    private void doUnorderedList(int n) throws IOException {
        sink.startUnorderedList();

        String x = repeat("*", n + 1);
        String x2 = repeat("*", n + 2);

        do {
            // Check if we need to recurse deeper
            if(currentLine.startsWith(x2)) {
                doUnorderedList(n + 1);
            }

            // When getting back, make sure we're still on "this" level
            if (!currentLine.startsWith(x)) {
                break;
            }

            // Remove the leading stars
            currentLine = currentLine.substring(n + 1).trim();

            sink.onItem(currentLine);
        } while (getLine());

        sink.endUnorderedList();
    }

    public boolean getLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return false;
        }

        currentLine = line.trim();

        return true;
    }

    static String repeat( String str, int repeat )
    {
        StringBuffer buffer = new StringBuffer( repeat * str.length() );
        for ( int i = 0; i < repeat; i++ )
        {
            buffer.append( str );
        }
        return buffer.toString();
    }
}
