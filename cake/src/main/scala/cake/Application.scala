package cake

import unfiltered.filter.Plan
import unfiltered.response._
import unfiltered.filter.request.ContextPath
import io.Source
import ems.Config

class Application extends Plan {
  def intent = {
    case ContextPath(cp, "/") => {
      val index = Source.fromInputStream(config.getServletContext.getResourceAsStream("/index.html")).getLines().mkString
      HtmlContent ~> ResponseString(index.replace("[EMS_SERVER]", Config.default.server.toString))
    }
    case x => Pass
  }
}