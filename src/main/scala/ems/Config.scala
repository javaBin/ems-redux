package ems.config

import java.io.File
import java.net.URI
import org.constretto._
import Constretto._
import scala.util.Properties
import org.constretto.internal.store.SystemPropertiesStore

case class ServerConfig(binary: File, mongo: String)

case class CacheConfig(events: Int = 30, sessions: Int = 30)

case class SqlConfig(host: String = "localhost",
                     port: Int = 5432,
                     database: String = "ems",
                     username: String = "ems",
                     password: String = "ems") {
  val url = s"jdbc:postgresql://$host:$port/$database"
  val driver = "org.postgresql.Driver"
}

object Config {
  val APP_HOME = Properties.envOrElse("APP_HOME", Properties.propOrElse("app.home", "."))

  private val constretto = {
    Constretto(List(
      inis(
        "classpath:config.ini",
        s"file:$APP_HOME/etc/config.ini"
      ),
      new SystemPropertiesStore()
    ))
  }

  val server: ServerConfig = ServerConfig(
    constretto[File]("server.binary"),
    constretto[String]("server.mongo")
  )

  val cache: CacheConfig = CacheConfig(
    constretto[Int]("cache.events"), constretto[Int]("cache.sessions")
  )

  override def toString = {
    List(server.toString, cache.toString).mkString("Config:\n", "\n", "\n")
  }
}
