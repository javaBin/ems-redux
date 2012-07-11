package no.java.ems

import util.Properties

object Main extends App {
  val port = Properties.envOrElse("PORT", "8080").toInt

  unfiltered.jetty.Http(port).plan(Resources).run()
}
