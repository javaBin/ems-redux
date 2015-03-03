package ems

import java.io.File
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import ems.security.{JAASAuthenticator, PropertyFileAuthenticator}

import scala.util.Properties

object Jetty extends App {
  val port = Properties.envOrElse("PORT", "8081").toInt
  val contextPath = Properties.propOrElse("contextPath", Properties.envOrElse("contextPath", "/server"))
  val authStrategy = Properties.propOrElse("auth-strategy", Properties.envOrElse("auth-strategy", "jaas"))
  val home = new File(Properties.propOrElse("app.home", Properties.envOrElse("app.home", ".")))
  val auth = authStrategy match {
    case "file" => PropertyFileAuthenticator[HttpServletRequest, HttpServletResponse](new File(home, "passwords.properties"))
    case _ => JAASAuthenticator
  }

  private val server = unfiltered.jetty.Server.http(port).context(contextPath) {
    _.plan(Resources(auth))
  }

  server.underlying.setSendDateHeader(true)
  server.run( _ => println("Running server at " + port))
}
