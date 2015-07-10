package ems.config

import java.io.File
import java.net.URI
import org.constretto._
import Constretto._
import scala.util.Properties
import org.constretto.internal.store.SystemPropertiesStore
import uritemplate._, Syntax._
import org.apache.commons.codec.digest._

case class ServerConfig(binary: File, mongo: String)

case class CacheConfig(events: Int = 30, sessions: Int = 30)

case class SessionPermalinks(map: Map[String, URITemplate]) {
  def expand(eventId: String, href: URI): Option[URI] = {
    map.get(eventId).map(_.expand("href" := hash(href))).map(URI.create)
  }

  def hash(href: URI): String = DigestUtils.sha256Hex(href.toString).trim
}

object SessionPermalinks {
  import org.json4s._
  import org.json4s.native.JsonMethods._

  private lazy val links: Map[String, SessionPermalinks] = {
    val f = {
      var f = new File(new File(Config.APP_HOME), "etc/permalinks.json")
      if (!f.exists) {
        f = new File(new File(Config.APP_HOME), "current/etc/permalinks.json")
      }
    }
    def toMap(jO: JObject) = jO.values.foldLeft(Map.empty[String, Map[String, URITemplate]]){case (m, (k,v)) => m.updated(k, (v match {
      case j: Map[_, _] => j.map{case (k,v) => k.toString -> URITemplate(v.toString)}.toMap
      case n => Map.empty[String, URITemplate]
    })) }
    val map : Map[String, Map[String, URITemplate]] = parseOpt(new java.io.FileReader(f)).
      collect{case j : JObject => toMap(j)}.getOrElse(Map.empty)
    map.mapValues(m => SessionPermalinks(m))
  }

  def fromConstrettoTags(): SessionPermalinks = {
    val tags = Properties.propOrElse("CONSTRETTO_TAGS", Properties.envOrElse("CONSTRETTO_TAGS", "default"))
    links.getOrElse(tags, SessionPermalinks(Map.empty))
  }
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
