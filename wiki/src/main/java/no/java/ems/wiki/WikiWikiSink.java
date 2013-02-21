package no.java.ems.wiki;


/**
 * @author <a href="mailto:trygve.laugstol@arktekk.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class WikiWikiSink implements WikiSink {
    private StringBuilder text = new StringBuilder();

    private int listIndent;
    private String listIndentString;

    // -----------------------------------------------------------------------
    // WikiSink Implementation
    // -----------------------------------------------------------------------

    public void startDocument() {
    }

    public void endDocument() {
    }

    public void startParagraph() {
    }

    public void endParagraph() {
        addLine("");
    }

    public void startUnorderedList() {
        listIndent++;
        listIndentString = DefaultWikiEngine.repeat("*", listIndent);
    }

    public void endUnorderedList() {
        listIndent--;

        if (listIndent == 0) {
            addLine("");
        } else {
            listIndentString = DefaultWikiEngine.repeat("*", listIndent);
        }
    }

    public void onHeading1(String s) {
        addLine("h1. " + s);
        addLine("");
    }

    public void onText(String line) {
        addLine(line);
    }

    public void onItem(String item) {
        addLine(" " + listIndentString + " " + item);
    }

    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    public String toString() {
        return text.toString();
    }

    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    private void addLine(String s) {
        text.append(s).append(EOL);
    }
}
