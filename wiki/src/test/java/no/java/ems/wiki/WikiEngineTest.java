package no.java.ems.wiki;

import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class WikiEngineTest {
    @Test
    public void test1() throws Exception {
        testId("1");
    }

    @Test
    public void test2() throws Exception {
        testId("2");
    }

    @Test
    public void test3() throws Exception {
        testId("3");
    }

    @Test
    public void test4() throws Exception {
        testId("4");
    }

    @Test
    public void test5() throws Exception {
        testId("5");
    }

    @Test
    public void test6() throws Exception {
        testId("6");
    }

    private void testId(String id) throws Exception {
        WikiEngine htmlEngine = new DefaultWikiEngine<DefaultHtmlWikiSink>(LoggingWikiSink.wrap(new DefaultHtmlWikiSink()));
        WikiEngine textEngine = new DefaultWikiEngine<WikiWikiSink>(LoggingWikiSink.wrap(new WikiWikiSink()));

        String wiki = IOUtils.toString(WikiEngineTest.class.getResourceAsStream("test-" + id + ".wiki"));
        String html = IOUtils.toString(WikiEngineTest.class.getResourceAsStream("test-" + id + ".html"));
        String text = IOUtils.toString(WikiEngineTest.class.getResourceAsStream("test-" + id + ".txt"));

        htmlEngine.transform(wiki);
        assertEquals(html, htmlEngine.getSink().toString());

        textEngine.transform(wiki);
        String generatedWiki = textEngine.getSink().toString();
        assertEquals(text, generatedWiki);

        // Now, pass the generated text back as input to the wiki parser to check that the generated wiki is
        // the same as the parsed one

        textEngine = new DefaultWikiEngine<WikiWikiSink>(new WikiWikiSink());
        textEngine.transform(generatedWiki);
        assertEquals(generatedWiki, textEngine.getSink().toString());
    }
}
