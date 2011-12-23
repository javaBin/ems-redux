package no.java.ems

import java.net.URI
import org.joda.time.format.ISODateTimeFormat
import net.hamnaberg.json.collection._
import no.java.http.URIBuilder

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 11/8/11
 * Time: 10:11 PM
 * To change this template use File | Settings | File Templates.
 */

object converters {

  def eventToItem(baseBuilder: URIBuilder): (Event) => Item = {
    e => {
      val dateFormat = ISODateTimeFormat.basicDateTimeNoMillis()
      val properties = Map(
        "name" -> e.title,
        "start" -> dateFormat.print(e.start),
        "end" -> dateFormat.print(e.end)
      ).map(toProperty).toList
      Item(baseBuilder.segments("events", e.id.get).build(), properties, new Link(baseBuilder.segments("events", e.id.get, "sessions").build(), "sessions", Some("Sessions")) :: Nil)
    }
  }

  def sessionToItem(baseBuilder: URIBuilder): (Session) => Item = {
    s => {
      val properties = Map(
        "title" -> s.sessionAbstract.title,
        "body" -> s.sessionAbstract.body.getOrElse(""),
        "lead" -> s.sessionAbstract.lead.getOrElse(""),
        "format" -> s.sessionAbstract.format.toString,
        "level" -> s.sessionAbstract.level.toString,
        "state" -> s.state.toString,
        "tags" -> s.tags.map(_.name).mkString(","),
        "keywords" -> s.keywords.map(_.name).mkString(",")
      ).map(toProperty).toList
      Item(baseBuilder.segments("events", s.eventId, "sessions", s.id.get).build(), properties, Nil)
    }
  }

  def toProperty: PartialFunction[(String, Any), Property] = {
    case (a, b) => new Property(a, Some(a.capitalize), Some(Value(b)))
  }

  def singleCollection: (Item) => JsonCollection = {
    item => JsonCollection(item.href, item.links, item)
  }

  def errorCollection(href: URI, message: ErrorMessage): JsonCollection = {
    JsonCollection(href, message)
  }
}