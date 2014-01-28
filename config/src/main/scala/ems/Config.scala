package ems.config

import java.io.File
import java.net.URI
import org.constretto._
import Constretto._
import scala.util.Properties

case class ServerConfig(binary: File, mongo: String, root: URI)

case class CryptConfig(algorithm: String = "DES", password: String = "changeme")

case class CacheConfig(events: Int = 30, sessions: Int = 30)

object Config {
  private val APP_HOME = Properties.envOrElse("APP_HOME", Properties.propOrElse("APP_HOME", "."))

  private val constretto = {
    Constretto(List(
      inis(
        "classpath:config.ini",
        "file:/opt/jb/ems-redux/config.ini",
        s"file:$APP_HOME/etc/config.ini"
      )
    ))
  }

  val server: ServerConfig = ServerConfig(
    constretto[File]("server.binary"),
    constretto[String]("server.mongo"),
    constretto.get[String]("server.root").map(URI.create).getOrElse(throw new IllegalArgumentException("Missing server root"))
  )
  val crypt: CryptConfig = CryptConfig(
    constretto[String]("crypt.algorithm"), constretto[String]("crypt.password")
  )

  val cache: CacheConfig = CacheConfig(
    constretto[Int]("cache.events"), constretto[Int]("cache.sessions")
  )
}
