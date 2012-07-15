package no.java.ems

import java.net.URI
import model._
import org.joda.time.format.DateTimeFormat
import net.hamnaberg.json.collection._
import no.java.util.URIBuilder
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
  val DateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'h:m:ss'Z'").withZoneUTC()

  def eventToItem(baseBuilder: URIBuilder): (Event) => Item = {
    e => {
      val properties = Map(
        "name" -> Some(e.name),
        "start" -> Some(DateFormat.print(e.start)),
        "end" -> Some(DateFormat.print(e.end))
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", e.id.get).build()
      val sessions = baseBuilder.segments("events", e.id.get, "sessions").build()
      Item(href, properties, new Link(sessions, "sessions", Some("Sessions")) :: Nil)
    }
  }

  def roomToItem(baseBuilder: URIBuilder, eventId: String): (Room) => Item = {
    r => {
      val properties = Map(
        "name" -> Some(r.name)
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", eventId, "rooms", r.id.get).build()
      Item(href, properties, Nil)
    }
  }

  def slotToItem(baseBuilder: URIBuilder, eventId: String): (Slot) => Item = {
    r => {
      val properties = Map(
        "start" -> Some(DateFormat.print(r.start)),
        "end" -> Some(DateFormat.print(r.end))
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", eventId, "slots", r.id.get).build()
      Item(href, properties, Nil)
    }
  }

  def sessionToItem(baseBuilder: URIBuilder): (Session) => Item = {
    s => {
      val tags = if (s.tags.isEmpty) None else Some(s.tags.map(_.name).mkString(","))
      val keywords = if (s.keywords.isEmpty) None else Some(s.keywords.map(_.name).mkString(","))
      val properties = Map(
        "title" -> Some(s.abs.title),
        "body" -> s.abs.body,
        "summary" -> s.abs.summary,
        "audience" -> s.abs.audience,
        "outline" -> s.abs.audience,
        "locale" -> Some(s.abs.language.getLanguage),
        "format" -> Some(s.abs.format.toString),
        "level" -> Some(s.abs.level.toString),
        "state" -> Some(s.state.toString),
        "tags" -> tags,
        "keywords" -> keywords
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", s.eventId, "sessions", s.id.get).build()
      val links = List(
        Link(URIBuilder(href).segments("attachments").build(), "attachments", Some("Attachments for %s".format(s.abs.title))),
        Link(URIBuilder(href).segments("speakers").build(), "speakers", Some("Speakers for %s".format(s.abs.title)))
      )
      Item(href, properties, links)
    }
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

  def speakerToItem(builder: URIBuilder, eventId: String, sessionId: String): (Speaker) => (Item) = {
    s => {
      val base = builder.segments("events", eventId, "sessions", sessionId, "speakers", s.contactId)
      Item(
        base.build(),
        List(
          Property("name", Some("Name"), Some(JString(s.name))),
          Property("bio", Some("Bio"), s.bio.map(JString(_)))
        ),
        s.photo.map(a => Link(builder.segments("binary", a.id.get).build(), "photo", None, Some(Render.IMAGE))).toList ++
          List(Link(base.segments("photo").build(), "attach-photo"))
      )
    }
  }

  def contactToItem(baseBuilder: URIBuilder): (Contact) => (Item) = {
    c => {
      Item(
        baseBuilder.segments("contacts", c.id.get).build(),
        List(
          Property("name", Some("Name"), Some(JString(c.name))),
          Property("bio", Some("Bio"), c.bio.map(JString(_))),
          Property("locale", Some("Locale"), Some(JString(c.locale.getLanguage))),
          Property("emails", Some("Emails"), Some(JArray(c.emails.map(e => JString(e.address)))))
        ),
        c.photo.map(a => Link(baseBuilder.segments("binary", a.id.get).build(), "photo", None, Some(Render.IMAGE))).toList ++
          List(Link(baseBuilder.segments("contacts", "photo").build(), "attach-photo"))
      )
    }
  }

  def toEvent(id: Option[String], template: Template): Event = {
    val name = template.getPropertyValue("name").map(_.values.toString).get
    val start = template.getPropertyValue("start").map(x => DateFormat.parseDateTime(x.values.toString)).get
    val end = template.getPropertyValue("end").map(x => DateFormat.parseDateTime(x.values.toString)).get
    val venue = template.getPropertyValue("venue").map(_.values.toString).get
    Event(id, name, start, end, venue, Nil, Nil)
  }

  def toContact(id: Option[String], template: Template): Contact = {
    val name = template.getPropertyValue("name").map(_.values.toString).get
    val bio = template.getPropertyValue("bio").map(_.values.toString)

    val emailMapper: PartialFunction[JValue, List[Email]] = {
      case JArray(list) => list.map(v => Email(v.values.toString))
    }

    val locale = template.getPropertyValue("locale").map(_.values.toString).map(x => new Locale(x)).getOrElse(new Locale("no"))
    val emails = template.getPropertyValue("emails").map(emailMapper).getOrElse(Nil)

    Contact(id, name, bio, emails, locale)
  }


  def toSession(eventId: String, id: Option[String], template: Template): Session = {
    val title = template.getPropertyValue("title").get.values.toString
    val body = template.getPropertyValue("body").map(_.values.toString)
    val outline = template.getPropertyValue("outline").map(_.values.toString)
    val audience = template.getPropertyValue("audience").map(_.values.toString)
    val summary = template.getPropertyValue("summary").map(_.values.toString)
    val format = template.getPropertyValue("format").map(x => Format(x.values.toString))
    val level = template.getPropertyValue("level").map(x => Level(x.values.toString))
    val language = template.getPropertyValue("lang").map(x => new Locale(x.values.toString))
    val state = template.getPropertyValue("state").map(x => State(x.values.toString))
    val tags = template.getPropertyValue("tags").toList.flatMap(x => x.values.toString.split(",").map(Tag(_)).toList)
    val keywords = template.getPropertyValue("keywords").toList.flatMap(x => x.values.toString.split(",").map(Keyword(_)).toList)
    val abs = Abstract(title, summary, body, audience, outline, language.getOrElse(new Locale("no")), level.getOrElse(Level.Beginner), format.getOrElse(Format.Presentation), Vector())
    val sess = Session(eventId, abs, state.getOrElse(State.Pending), tags.toSet[Tag], keywords.toSet[Keyword])
    sess.copy(id = id)
  }

  def toAttachment(template: Template): URIAttachment = {
    val href = template.getPropertyValue("href").map(x => URI.create(x.values.toString)).get
    val name = template.getPropertyValue("name").get.values.toString
    val sizeFilter: PartialFunction[JValue, Long] = {
      case JInt(x) => x.toLong
    }
    val size = template.getPropertyValue("size").map(sizeFilter)
    val mediaType = template.getPropertyValue("type").flatMap(x => MIMEType(x.values.toString))
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