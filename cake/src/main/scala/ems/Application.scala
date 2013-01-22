package ems

import unfiltered.filter.Plan
import unfiltered.response._
import unfiltered.filter.request.ContextPath
import io.Source

class Application extends Plan {
  def intent = {
    case ContextPath(cp, "/") => {
      val index = Source.fromInputStream(config.getServletContext.getResourceAsStream("/index.html")).getLines().mkString
      HtmlContent ~> ResponseString(index.replace("[EMS_SERVER]", EmsConfig.server.toString))
    }
    case x => Pass
    //case Path("/events") => Html5(Pages.events(URI.create("http://localhost:8081/events")))
  }
}
