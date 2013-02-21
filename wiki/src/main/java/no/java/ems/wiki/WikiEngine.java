package no.java.ems.wiki;

import java.io.IOException;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public interface WikiEngine<S extends WikiSink> {
    void transform(String text) throws IOException;

    S getSink();
}
