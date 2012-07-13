package no.java.ems

import scala.util.Properties

object Main extends App {
  val port = Properties.envOrElse("PORT", "8081").toInt

  unfiltered.jetty.Http(port).plan(Resources).run()
}