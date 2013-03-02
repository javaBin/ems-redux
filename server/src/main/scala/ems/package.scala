import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import language.implicitConversions

package ems {
  package object model {
    implicit class HtmlCleaner(val string: String) extends AnyVal{
      def noHtml = Jsoup.clean(string, Whitelist.none())
    }
  }
}

