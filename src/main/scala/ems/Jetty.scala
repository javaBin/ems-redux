package ems

import scala.util.Properties

object Jetty extends App {
  val port = Properties.envOrElse("PORT", "8081").toInt

  private val server = unfiltered.jetty.Server.http(port).context("/server") {
    _.plan(Resources(ems.security.JAASAuthenticator))
  }

  server.underlying.setSendDateHeader(true)
  server.run( _ => println("Running server at " + port))
}
