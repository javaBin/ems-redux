package no.java.ems

import java.net.URI
import org.joda.time.format.ISODateTimeFormat
import net.hamnaberg.json.collection._
import no.java.http.URIBuilder
import java.util.Locale
import net.liftweb.json.JsonAST._

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 11/8/11
 * Time: 10:11 PM
 * To change this template use File | Settings | File Templates.
 */

object converters {
  val dateFormat = ISODateTimeFormat.basicDateTimeNoMillis()

  def eventToItem(baseBuilder: URIBuilder): (Event) => Item = {
    e => {
      val properties = Map(
        "name" -> Some(e.title),
        "start" -> Some(dateFormat.print(e.start)),
        "end" -> Some(dateFormat.print(e.end))
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", e.id.get).build()
      val sessions = baseBuilder.segments("events", e.id.get, "sessions").build()
      Item(
        href,
        properties,
        new Link(sessions, "sessions", Some("Sessions")) :: Link(href, "event", Some(e.title)) :: Nil)
    }
  }

  def toEvent(id: Option[String], template: Template): Event = {
    val name = template.getPropertyValue("name").map(_.values.toString).get
    val start = template.getPropertyValue("start").map(x => dateFormat.parseDateTime(x.values.toString)).get
    val end = template.getPropertyValue("end").map(x => dateFormat.parseDateTime(x.values.toString)).get
    Event(id, name, start, end)
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
      val href = baseBuilder.segments("events", s.eventId, "sessions", s.id.get).build()
      val links = List(
        Link(href, "session", Some(s.sessionAbstract.title)),
        Link(URIBuilder(href).segments("attachments").build(), "attachments", Some("Attachments for %s".format(s.sessionAbstract.title)))
      )
      Item(href, properties, links)
    }
  }

  def toSession(eventId: String, id: Option[String], template: Template) : Session = {
    val title = template.getPropertyValue("title").get.values.toString
    val body = template.getPropertyValue("body").map(_.values.toString)
    val lead = template.getPropertyValue("lead").map(_.values.toString)
    val format = template.getPropertyValue("format").map(x => Format(x.values.toString))
    val level = template.getPropertyValue("level").map(x => Level(x.values.toString))
    val language = template.getPropertyValue("lang").map(x => new Locale(x.values.toString))
    val state = template.getPropertyValue("state").map(x => State(x.values.toString))
    val tags = template.getPropertyValue("tags").toList.flatMap(x=> x.values.toString.split(",").map(Tag(_)).toList)
    val keywords = template.getPropertyValue("tags").toList.flatMap(x=> x.values.toString.split(",").map(Keyword(_)).toList)
    val abs = SessionAbstract(title, lead, body, language.getOrElse(new Locale("no")), level.getOrElse(Level.Beginner), format.getOrElse(Format.Presentation), Vector())
    val sess = Session(eventId, abs, state.getOrElse(State.Pending), tags.toSet[Tag], keywords.toSet[Keyword])
    sess.copy(id = id)
  }


  val attachmentToItem: (URIAttachment) => (Item) = {
    a => {
      Item(
        a.href,
        List(
          Property("href", Some("Href"), Some(JString(a.href.toString))),
          Property("name", Some("Name"), Some(JString(a.name))),
          Property("size", Some("Size"), a.size.map((JInt(_)))),
          Property("type", Some("Type"), Some(JString(a.mediaType.toString)))
        ),
        Nil
      )
    }
  }

  def toAttachment(template: Template) : URIAttachment = {
    val href = template.getPropertyValue("href").map(x => URI.create(x.values.toString)).get
    val name = template.getPropertyValue("name").get.values.toString
    val sizeFilter: PartialFunction[JValue, Long] = {
      case JInt(x) => x.toLong
    }
    val size = template.getPropertyValue("size").map(sizeFilter)
    val mediaType = template.getPropertyValue("type").map(x => MIMEType(x.values.toString))
    URIAttachment(href, name, size, mediaType)
  }

  private[ems] def toProperty: PartialFunction[(String, Option[Any]), Property] = {
    case (a, b) => new Property(a, Some(a.capitalize), b.map(toValue(_)))
  }

  def toValue(any: Any): JValue = any match {
    case x: String => JString(x)
    case x: Int => JInt(x)
    case x: Long => JInt(x)
    case x: Double => JDouble(x)
    case x: List[_] => JArray(x.map(z => toValue(z)))
    case x: Boolean => JBool(x)
    case null => JNull
    case _ => throw new IllegalArgumentException("Unknown value")
  }
}