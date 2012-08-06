package ems

import unfiltered.filter.{Planify, Plan}
import unfiltered.request.Path
import unfiltered.response.Html5

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

class Application extends Plan {
  def intent = {
    case Path("/") => Html5(Snippets.template(<h1>Hello</h1> <p>From EMS</p>))
    //case Path("/events") => Html5(Pages.events(URI.create("http://localhost:8081/events")))
  }
}
