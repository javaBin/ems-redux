package ems

import unfiltered.request._
import unfiltered.response._
import unfilteredx.unfilteredx.StaticResourcesPlan
import unfiltered.filter.Planify

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Cake extends App {
  val intent = Planify {
    case Path("/") => Html5(Snippets.template(<h1>Hello</h1> <p>From EMS</p>))
    //case Path("/events") => Html5(Pages.events(URI.create("http://localhost:8081/events")))
  }
  unfiltered.jetty.Http(8080).plan(StaticResourcesPlan).plan(intent).plan(EmsProxy).run(s => {
    println("The cake is a lie; Started")
  })
}
