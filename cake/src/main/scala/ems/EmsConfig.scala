package ems

import java.net.URI
import scala.util.Properties

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object EmsConfig {
  val root = URI.create(Properties.propOrElse("ems-root", "http://localhost:8081"))
}
