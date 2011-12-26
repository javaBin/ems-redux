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
    val name = template.getPropertyValue("name").map(_.value.toString).get
    val start = template.getPropertyValue("start").map(x => dateFormat.parseDateTime(x.value.toString)).get
    val end = template.getPropertyValue("end").map(x => dateFormat.parseDateTime(x.value.toString)).get
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
    sess.copy(id = id)
  }


  val attachmentToItem: (URIAttachment) => (Item) = {
    a => {
      Item(
        a.href,
        List(
          Property("href", Some("Href"), Some(Value(a.href))),
          Property("name", Some("Name"), Some(Value(a.name))),
          Property("size", Some("Size"), a.size.map((Value(_)))),
          Property("type", Some("Type"), Some(Value(a.mediaType.toString)))
        ),
        Nil
      )
    }
  }

  def toAttachment(template: Template) : URIAttachment = {
    val href = template.getPropertyValue("href").map(x => URI.create(x.value.toString)).get
    val name = template.getPropertyValue("name").get.value.toString
    val sizeFilter: PartialFunction[Value, Long] = {
      case NumericValue(x) => x.toLong
    }
    val size = template.getPropertyValue("size").map(sizeFilter)
    val mediaType = template.getPropertyValue("type").map(x => MIMEType(x.value.toString))
    URIAttachment(href, name, size, mediaType)
  }

  private[ems] def toProperty: PartialFunction[(String, Option[Any]), Property] = {
    case (a, b) => new Property(a, Some(a.capitalize), b.map(Value(_)))
  }
}