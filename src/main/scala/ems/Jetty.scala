package ems

import java.io.File
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import ems.security.{JAASAuthenticator, PropertyFileAuthenticator}
import ems.storage.{FilesystemBinaryStorage, SQLStorage, Migration}

import scala.util.Properties

object Jetty extends App {
  val port = Properties.propOrElse("PORT", Properties.envOrElse("PORT", "8081")).toInt
  val contextPath = Properties.propOrElse("contextPath", Properties.envOrElse("contextPath", "/server"))
  val authStrategy = Properties.propOrElse("auth-strategy", Properties.envOrElse("auth-strategy", "file"))
  val home = new File(Properties.propOrElse("app.home", Properties.envOrElse("APP_HOME", ".")))

  val auth = authStrategy match {
    case "file" => PropertyFileAuthenticator[HttpServletRequest, HttpServletResponse](new File(home, "etc/passwords.properties"))
    case _ => new JAASAuthenticator[HttpServletRequest, HttpServletResponse]
  }

  val config = Config.load(home)

  private val server = unfiltered.jetty.Server.http(port).context(contextPath) {
    _.plan(Resources(new SQLStorage(config.sql,  new FilesystemBinaryStorage(new File(home, "binary"))),auth))
  }

  server.underlying.setSendDateHeader(true)
  server.run( _ => {
      Migration.runMigration(config.sql)
      println("[ EMS ] Running server at " + port)
    }
  )
}
