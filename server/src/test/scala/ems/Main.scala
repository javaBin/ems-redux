package no.java.ems

import scala.util.Properties
import no.java.ems.security.JAASAuthenticator

object Main extends App {
  val port = Properties.envOrElse("PORT", "8081").toInt

  unfiltered.jetty.Http(port).plan(Resources(JAASAuthenticator)).run()
}