package ems

import unfiltered.filter.Plan
import unfiltered.response._

class Application extends Plan {
  def intent = {
    case x => Pass
    //case ContextPath(cp, "/") => (config.getServletContext.getResourceAsStream("/index.html"))
    //case Path("/events") => Html5(Pages.events(URI.create("http://localhost:8081/events")))
  }
}
