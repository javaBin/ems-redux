package ems

import java.nio.charset.Charset

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities.EscapeMode
import org.jsoup.safety.Whitelist

package object model {
  implicit class HtmlCleaner(val string: String) extends AnyVal{
    def noHtml = {
      val settings = new Document.OutputSettings()
      settings.indentAmount(0)
      settings.escapeMode(EscapeMode.xhtml)
      settings.prettyPrint(false)
      settings.charset(Charset.forName("UTF-8"))
      Jsoup.clean(string, "", Whitelist.none(), settings).
        replace("&quot;", "\"").
        replace("&apos;", "'").
        replace("&amp;", "&")

    }
  }
}
