import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import language.implicitConversions

package ems {

import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities.EscapeMode

package object model {
    implicit class HtmlCleaner(val string: String) extends AnyVal{
      def noHtml = {
        val settings = new Document.OutputSettings()
        settings.indentAmount(0)
        settings.escapeMode(EscapeMode.xhtml)
        settings.prettyPrint(false)
        Jsoup.clean(string, "", Whitelist.none(), settings)
      }
    }
  }
}

