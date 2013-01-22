package ems

import java.net.URI
import scala.util.Properties

object EmsConfig {
  val server = URI.create(Properties.propOrElse("ems-server", "http://localhost:8081/server/"))
}
