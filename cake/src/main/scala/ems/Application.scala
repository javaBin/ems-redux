package ems

import unfiltered.filter.{Planify, Plan}
import unfiltered.request.Path
import unfiltered.response.Html5
import unfiltered.filter.request.ContextPath

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

class Application extends Plan {
  def intent = {
    case ContextPath(cp, "/") => Html5(Snippets.template(cp, <div id="content"><h1>Hello</h1> <p>From EMS</p></div>))
    //case Path("/events") => Html5(Pages.events(URI.create("http://localhost:8081/events")))
  }
}
