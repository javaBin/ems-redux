package ems

import java.net.URI
import scala.util.Properties

object EmsConfig {
  val server = URI.create(Properties.propOrElse("ems-server", "/server/"))
  val password = Properties.propOrElse("ems-password", "develope")
}
