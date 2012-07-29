package no.java.ems

import java.net.URI
import model._
import org.joda.time.format.DateTimeFormat
import net.hamnaberg.json.collection._
import no.java.util.URIBuilder
import java.util.Locale
import net.hamnaberg.json.collection.Value.{NullValue, BooleanValue, StringValue, NumberValue}

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

  def attachmentToItem(baseURIBuilder: URIBuilder): (URIAttachment) => (Item) = {
    a => {
      val href = {
        val h = a.href
        if (!h.isAbsolute) {
          baseURIBuilder.segments("binary", h.getPath).build()
        }
        else {
          h
        }
      }
      Item(
        href,
        List(
          ValueProperty("href", Some("Href"), Some(StringValue(href.toString))),
          ValueProperty("name", Some("Name"), Some(StringValue(a.name))),
          ValueProperty("size", Some("Size"), a.size.map(s => (NumberValue(BigDecimal(s))))),
          ValueProperty("type", Some("Type"), Some(StringValue(a.mediaType.toString)))
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
          ValueProperty("name", Some("Name"), Some(StringValue(s.name))),
          ValueProperty("bio", Some("Bio"), s.bio.map(StringValue(_)))
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
          ValueProperty("name", Some("Name"), Some(StringValue(c.name))),
          ValueProperty("bio", Some("Bio"), c.bio.map(StringValue(_))),
          ValueProperty("locale", Some("Locale"), Some(StringValue(c.locale.getLanguage))),
          ListProperty("emails", Some("Emails"), c.emails.map(e => StringValue(e.address)))
        ),
        c.photo.map(a => Link(baseBuilder.segments("binary", a.id.get).build(), "photo", None, Some(Render.IMAGE))).toList ++
          List(Link(baseBuilder.segments("contacts", "photo").build(), "attach-photo"))
      )
    }
  }

  def toEvent(id: Option[String], template: Template): Event = {
    val name = template.getPropertyValue("name").map(_.value.toString).get
    val start = template.getPropertyValue("start").map(x => DateFormat.parseDateTime(x.value.toString)).get
    val end = template.getPropertyValue("end").map(x => DateFormat.parseDateTime(x.value.toString)).get
    val venue = template.getPropertyValue("venue").map(_.value.toString).get
    Event(id, name, start, end, venue, Nil, Nil)
  }

  def toContact(id: Option[String], template: Template): Contact = {
    val name = template.getPropertyValue("name").map(_.value.toString).get
    val bio = template.getPropertyValue("bio").map(_.value.toString)

    val locale = template.getPropertyValue("locale").map(_.value.toString).map(x => new Locale(x)).getOrElse(new Locale("no"))
    val emails = template.getPropertyAsSeq("emails").map(e => Email(e.value.toString)).toList

    Contact(id, name, bio, emails, locale)
  }


  def toSession(eventId: String, id: Option[String], template: Template): Session = {
    val title = template.getPropertyValue("title").get.value.toString
    val body = template.getPropertyValue("body").map(_.value.toString)
    val outline = template.getPropertyValue("outline").map(_.value.toString)
    val audience = template.getPropertyValue("audience").map(_.value.toString)
    val summary = template.getPropertyValue("summary").map(_.value.toString)
    val format = template.getPropertyValue("format").map(x => Format(x.value.toString))
    val level = template.getPropertyValue("level").map(x => Level(x.value.toString))
    val language = template.getPropertyValue("lang").map(x => new Locale(x.value.toString))
    val state = template.getPropertyValue("state").map(x => State(x.value.toString))
    val tags = template.getPropertyAsSeq("tags").map(t => Tag(t.value.toString))
    val keywords = template.getPropertyAsSeq("keywords").map(k => Keyword(k.value.toString))
    val abs = Abstract(title, summary, body, audience, outline, language.getOrElse(new Locale("no")), level.getOrElse(Level.Beginner), format.getOrElse(Format.Presentation), Vector())
    val sess = Session(eventId, abs, state.getOrElse(State.Pending), tags.toSet[Tag], keywords.toSet[Keyword])
    sess.copy(id = id)
  }

  def toAttachment(template: Template): URIAttachment = {
    val href = template.getPropertyValue("href").map(x => URI.create(x.value.toString)).get
    val name = template.getPropertyValue("name").get.value.toString
    val sizeFilter: PartialFunction[Value[_], Long] = {
      case NumberValue(x) => x.toLong
    }
    val size = template.getPropertyValue("size").map(sizeFilter)
    val mediaType = template.getPropertyValue("type").flatMap(x => MIMEType(x.value.toString))
    URIAttachment(href, name, size, mediaType)
  }

  private[ems] def toProperty: PartialFunction[(String, Option[Any]), Property] = {
    case (a, b) => ValueProperty(a, Some(a.capitalize), b.map(toValue(_)))
  }

  def toValue(any: Any): Value[_] = any match {
    case x: String => StringValue(x)
    case x: Int => NumberValue(BigDecimal(x))
    case x: Long => NumberValue(BigDecimal(x))
    case x: Double => NumberValue(BigDecimal(x))
    case x: Boolean => BooleanValue(x)
    case null => NullValue
    case _ => throw new IllegalArgumentException("Unknown value")
  }
}