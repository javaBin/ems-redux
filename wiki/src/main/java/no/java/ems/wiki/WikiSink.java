package no.java.ems.wiki;

/**
 * @author <a href="mailto:trygve.laugstol@arktekk.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public interface WikiSink {
    String EOL = System.getProperty("line.separator");

    void startDocument();

    void endDocument();

    void startParagraph();

    void endParagraph();

    void startUnorderedList();

    void endUnorderedList();

    void onHeading1(String s);

    void onText(String line);

    void onItem(String item);
}
