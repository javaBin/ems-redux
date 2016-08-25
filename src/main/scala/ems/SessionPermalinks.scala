package ems

import java.io.File
import java.net.URI

import ems.model.Session
import org.apache.commons.codec.digest._
import uritemplate.Syntax._
import uritemplate._

case class Expansion(variable: String, template: URITemplate)

case class SessionPermalinks(map: Map[String, Expansion]) {
  def expand(eventId: String, session: Session, href: URI): Option[URI] = {
    map.get(eventId).flatMap(
      exp => exp.variable match {
        case "title" => expandTitle(eventId, session.abs.title)
        case "href" => expandHref(eventId, href)
        case _ => None
      }
    )
  }

  private[ems] def expandHref(eventId: String, href: URI): Option[URI] = {
    map.get(eventId).map(exp => exp.template.expand(exp.variable := hash(href))).map(URI.create)
  }

  private[ems] def expandTitle(eventId: String, title: String): Option[URI] = {
    map.get(eventId).map(exp => exp.template.expand(exp.variable := escapeTitle(title))).map(URI.create)
  }

  def escapeTitle(title: String) = {
    title.trim.toLowerCase.
      replaceAll(" +", "-").
      replace("æ", "ae").
      replace("ø", "oe").
      replace("aa", "å").
      replaceAll("[^a-z0-9-]", "")
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

    parse(f)
  }

  private def parse(f: File): Map[String, SessionPermalinks] = {
    def parseExpansion(v: JValue): Expansion = {
      v match {
        case obj@JObject(_) => Expansion(
          (obj \ "variable").toString,
          URITemplate((obj \ "template").toString)
        )
        case j => sys.error("Failed" + j)
      }
    }

    def parsePermaLinks(v: JObject): SessionPermalinks = {
      SessionPermalinks(v.obj.foldLeft(Map.empty[String, Expansion]){ case (map, (key, value)) =>
        map + (key -> parseExpansion(value))
      })
    }


    def parseIt(obj: JObject): Map[String, SessionPermalinks] = {
      obj.obj.foldLeft(Map.empty[String, SessionPermalinks]){case (map, (key, value)) =>
        map + (key -> parsePermaLinks(value.asInstanceOf[JObject]))
      }
    }


    parseOpt(f).collect{ case j: JObject => j}.map(parseIt).getOrElse(Map.empty)
  }

  def fromEnvironment(name: String): SessionPermalinks = links.getOrElse(name, SessionPermalinks(Map.empty))
}
