package ems

import uritemplate._, Syntax._
import scala.util.Properties
import org.apache.commons.codec.digest._
import java.io.File
import java.net.URI

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
      var f = new File(Jetty.home, "etc/permalinks.json")
      if (!f.exists) {
        f = new File(Jetty.home, "current/etc/permalinks.json")
      }
      f
    }
    def toMap(jO: JObject) = jO.values.foldLeft(Map.empty[String, Map[String, URITemplate]]){case (m, (k,v)) => m.updated(k, (v match {
      case j: Map[_, _] => j.map{case (k,v) => k.toString -> URITemplate(v.toString)}.toMap
      case n => Map.empty[String, URITemplate]
    })) }
    val map : Map[String, Map[String, URITemplate]] = parseOpt(new java.io.FileReader(f)).
      collect{case j : JObject => toMap(j)}.getOrElse(Map.empty)
    map.mapValues(m => SessionPermalinks(m))
  }

  def fromEnvironment(name: String): SessionPermalinks = links.getOrElse(name, SessionPermalinks(Map.empty))
}
