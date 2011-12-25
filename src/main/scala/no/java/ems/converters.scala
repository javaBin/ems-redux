package no.java.ems

import java.net.URI
import org.joda.time.format.ISODateTimeFormat
import net.hamnaberg.json.collection._
import no.java.http.URIBuilder
import java.util.Locale

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
        "name" -> Some(e.title),
        "start" -> Some(dateFormat.print(e.start)),
        "end" -> Some(dateFormat.print(e.end))
      ).map(toProperty).toList
      Item(baseBuilder.segments("events", e.id.get).build(), properties, new Link(baseBuilder.segments("events", e.id.get, "sessions").build(), "sessions", Some("Sessions")) :: Nil)
    }
  }

  def sessionToItem(baseBuilder: URIBuilder): (Session) => Item = {
    s => {
      val tags = if(s.tags.isEmpty) None else Some(s.tags.map(_.name).mkString(","))
      val keywords = if(s.keywords.isEmpty) None else Some(s.keywords.map(_.name).mkString(","))
      val properties = Map(
        "title" -> Some(s.sessionAbstract.title),
        "body" -> s.sessionAbstract.body,
        "lead" -> s.sessionAbstract.lead,
        "lang" -> Some(s.sessionAbstract.language.getLanguage),
        "format" -> Some(s.sessionAbstract.format.toString),
        "level" -> Some(s.sessionAbstract.level.toString),
        "state" -> Some(s.state.toString),
        "tags" -> tags,
        "keywords" -> keywords
      ).map(toProperty).toList
      Item(baseBuilder.segments("events", s.eventId, "sessions", s.id.get).build(), properties, Nil)
    }
  }

  def toSession(eventId: String, template: Template) : Session = {
    val title = template.getPropertyValue("title").get.value.toString
    val body = template.getPropertyValue("body").map(_.value.toString)
    val lead = template.getPropertyValue("lead").map(_.value.toString)
    val format = template.getPropertyValue("format").map(x => Format(x.value.toString))
    val level = template.getPropertyValue("level").map(x => Level(x.value.toString))
    val language = template.getPropertyValue("lang").map(x => new Locale(x.value.toString))
    val state = template.getPropertyValue("state").map(x => State(x.value.toString))
    val tags = template.getPropertyValue("tags").toList.flatMap(x=> x.value.toString.split(",").map(Tag(_)).toList)
    val keywords = template.getPropertyValue("tags").toList.flatMap(x=> x.value.toString.split(",").map(Keyword(_)).toList)
    val abs = SessionAbstract(title, lead, body, language.getOrElse(new Locale("no")), level.getOrElse(Level.Beginner), format.getOrElse(Format.Presentation), Vector())
    val sess = Session(eventId, abs, state.getOrElse(State.Pending), tags.toSet[Tag], keywords.toSet[Keyword])
    sess
  }

  private[ems] def toProperty: PartialFunction[(String, Option[Any]), Property] = {
    case (a, b) => new Property(a, Some(a.capitalize), b.map(Value(_)))
  }

  private[ems] def singleCollection: (Item) => JsonCollection = {
    item => JsonCollection(item.href, item.links, item)
  }
}