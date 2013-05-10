package ems

import java.net.URI
import model._
import net.hamnaberg.json.collection._
import util.URIBuilder
import java.util.Locale
import net.hamnaberg.json.collection.Value._
import security.User
import util.RFC3339


object converters {

  def eventToItem(baseBuilder: URIBuilder): (Event) => Item = {
    e => {
      val properties = Map(
        "name" -> Some(e.name),
        "slug" -> Some(e.slug),
        "start" -> Some(RFC3339.format(e.start)),
        "end" -> Some(RFC3339.format(e.end))
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", e.id.get)
      val links = List(
        Link(href.segments("sessions").build(), "session collection", Some("Sessions")),
        Link(href.segments("slots").build(), "slot collection", Some("Slots")),
        Link(href.segments("rooms").build(), "room collection", Some("Rooms"))
      )
      Item(href.build(), properties, links)
    }
  }

  def roomToItem(baseBuilder: URIBuilder, eventId: String): (Room) => Item = {
    r => {
      val properties = Map(
        "name" -> Some(r.name)
      ).map(toProperty).toList
      val rooms = baseBuilder.segments("events", eventId, "rooms")
      val href = rooms.segments(r.id.get).build()
      Item(href, properties, Link(rooms.build(), "room collection", Some("Rooms")) :: Nil)
    }
  }

  def slotToItem(baseBuilder: URIBuilder, eventId: String): (Slot) => Item = {
    r => {
      val properties = Map(
        "start" -> Some(RFC3339.format(r.start)),
        "end" -> Some(RFC3339.format(r.end))
      ).map(toProperty).toList
      val slots = baseBuilder.segments("events", eventId, "slots")
      val href = slots.segments(r.id.get).build()
      Item(href, properties,  Link(slots.build(), "slot collection", Some("Slots")) :: Nil)
    }
  }

  def toSlot(template: Template, id: Option[String] = None): Slot = Slot(
    id,
    template.getPropertyValue("start").map(v => RFC3339.parseDateTime(v.toString).right.get).get,
    template.getPropertyValue("end").map(v => RFC3339.parseDateTime(v.toString).right.get).get
  )

  def toRoom(template: Template, id: Option[String] = None): Room = Room(
    id,
    template.getPropertyValue("name").map(_.toString).get
  )

  def sessionToItem(baseBuilder: URIBuilder)(implicit u: User): (Session) => Item = {
    s => {
      val properties = Map(
        "title" -> Some(s.abs.title),
        "slug" -> Some(s.slug),
        "body" -> s.abs.body,
        "summary" -> s.abs.summary,
        "audience" -> s.abs.audience,
        "lang" -> Some(s.abs.language.getLanguage),
        "format" -> Some(s.abs.format.toString),
        "level" -> Some(s.abs.level.toString),
        "state" -> Some(s.state.toString),
        "keywords" -> Some(s.keywords.toSeq.map(_.name).filterNot(_.trim.isEmpty)).filterNot(_.isEmpty)
      ) ++ handlePrivateProperties(u, s)
      val filtered = properties.filter{case (k,v) => v.isDefined}.map(toProperty).toList

      val href = baseBuilder.segments("events", s.eventId, "sessions", s.id.get).build()
      Item(href, filtered, createSessionLinks(baseBuilder, href, s))
    }
  }


  private def handlePrivateProperties(u: User, s: Session): Seq[(String, Option[Any])] = {
    if (u.authenticated) {
      Seq(
        "tags" -> Some(s.tags.toSeq.map(_.name).filterNot(_.trim.isEmpty)),
        "outline" -> s.abs.outline,
        "equipment" -> s.abs.equipment
      )
    }
    else {
      Nil
    }
  }

  private def createSessionLinks(baseURIBuilder: URIBuilder, href: URI, s: Session): List[Link] = {
    val links = List.newBuilder[Link]

    links ++= List(
      Link(URIBuilder(href).segments("attachments").build(), "attachment collection", Some("Attachments")),
      Link(URIBuilder(href).segments("speakers").build(), "speaker collection", Some("Speakers")),
      Link(URIBuilder(href).segments("tags").build(), "session tag", Some("Tag session")),
      Link(URIBuilder(href).segments("slot").build(), "session slot", Some("Assign a slot")),
      Link(URIBuilder(href).segments("room").build(), "session room", Some("Assign a room"))
    )
    links ++= s.attachments.map(a => Link(if (a.href.getHost != null) a.href else baseURIBuilder.segments("binary", a.href.toString).build(), getRel(a), Some(a.name)))
    links ++= s.room.map(r => Link(baseURIBuilder.segments("events", s.eventId, "rooms", r.id.get).build(), "room item", Some(r.name)))
    links ++= s.slot.map(slot => Link(baseURIBuilder.segments("events", s.eventId, "slots", slot.id.get).build(), "slot item", Some(formatSlot(slot))))
    links ++= s.speakers.map(speaker => Link(URIBuilder(href).segments("speakers", speaker.id.get).build(), "speaker item", Some(speaker.name)))

    links.result()
  }

  def formatSlot(slot: Slot): String = {
    RFC3339.format(slot.start) + "+" + RFC3339.format(slot.end)
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

  def speakerToItem(builder: URIBuilder, eventId: String, sessionId: String)(implicit user: User): (Speaker) => (Item) = {
    s => {
      val auths = if (user.authenticated) {
        List(
          ListProperty("tags", Some("Tags"), s.tags.map(t => StringValue(t.name)).toSeq),
          ValueProperty("email", Some("Email"), Some(StringValue(s.email))),
          ValueProperty("zip-code", Some("Zip Code"), s.zipCode.map(StringValue(_)))
        )
      } else {
        Nil
      }

      val base = builder.segments("events", eventId, "sessions", sessionId, "speakers", s.id.get)
      val data = List(
        ValueProperty("name", Some("Name"), Some(StringValue(s.name))),
        ValueProperty("bio", Some("Bio"), s.bio.map(StringValue(_)))
      ) ++ auths

      Item(
        base.build(),
        data,
        s.photo.map(a => Link(builder.segments("binary", a.id.get).build(), "photo", None, Some(Render.IMAGE))).toList ++
          List(Link(base.segments("photo").build(), "attach-photo"))
      )
    }
  }

  def toEvent(template: Template, id: Option[String] = None): Event = {
    val name = template.getPropertyValue("name").map(_.value.toString).get
    val start = template.getPropertyValue("start").map(x => RFC3339.parseDateTime(x.value.toString).right.get).get
    val end = template.getPropertyValue("end").map(x => RFC3339.parseDateTime(x.value.toString).right.get).get
    val venue = template.getPropertyValue("venue").map(_.value.toString).get
    Event(id, name, Slug.makeSlug(name), start, end, venue, Nil, Nil)
  }

  def toSession(eventId: String, id: Option[String], template: Template): Session = {
    val title = template.getPropertyValue("title").get.value.toString
    val body = template.getPropertyValue("body").map(_.value.toString)
    val outline = template.getPropertyValue("outline").map(_.value.toString)
    val audience = template.getPropertyValue("audience").map(_.value.toString)
    val equipment = template.getPropertyValue("equipment").map(_.value.toString)
    val summary = template.getPropertyValue("summary").map(_.value.toString)
    val format = template.getPropertyValue("format").map(x => Format(x.value.toString))
    val level = template.getPropertyValue("level").map(x => Level(x.value.toString))
    val language = template.getPropertyValue("lang").map(x => new Locale(x.value.toString))
    val state = template.getPropertyValue("state").map(x => State(x.value.toString))
    val tags = template.getPropertyAsSeq("tags").map(t => Tag(t.value.toString))
    val keywords = template.getPropertyAsSeq("keywords").map(k => Keyword(k.value.toString))
    val abs = Abstract(title, summary, body, audience, outline, equipment, language.getOrElse(new Locale("no")), level.getOrElse(Level.Beginner), format.getOrElse(Format.Presentation))
    val sess = Session(eventId, abs, state.getOrElse(State.Pending), tags.toSet[Tag], keywords.toSet[Keyword])
    sess.copy(id = id)
  }

  def toSpeaker(template: Template, id: Option[String] = None): Speaker = {
    val name = template.getPropertyValue("name").get.value.toString
    val email = template.getPropertyValue("email").get.value.toString
    val bio = template.getPropertyValue("bio").map(_.value.toString)
    val zipCode = template.getPropertyValue("zip-code").map(_.value.toString)
    val tags = template.getPropertyAsSeq("tags").map(t => Tag(t.value.toString)).toSet[Tag]
    Speaker(id, name, email, zipCode, bio, tags)
  }

  def toAttachment(template: Template, id: Option[String] = None): URIAttachment = {
    val href = template.getPropertyValue("href").map(x => URI.create(x.value.toString)).get
    val name = template.getPropertyValue("name").get.value.toString
    val sizeFilter: PartialFunction[Value[_], Long] = {
      case NumberValue(x) => x.toLong
    }
    val size = template.getPropertyValue("size").map(sizeFilter)
    val mediaType = template.getPropertyValue("type").flatMap(x => MIMEType(x.value.toString))
    URIAttachment(id, href, name, size, mediaType)
  }

  private[ems] def toProperty: PartialFunction[(String, Option[Any]), Property] = {
    case (a, Some(x: Seq[_])) => ListProperty(a, Some(a.capitalize), x.map(toValue(_)))
    case (a, Some(x: Map[_, _])) => ObjectProperty(a, Some(a.capitalize), x.map{case (k: Any, v: Any) => k.toString -> toValue(v)}.toMap)
    case (a, b) => ValueProperty(a, Some(a.capitalize), b.map(toValue(_)))
  }

  private def getRel(a: URIAttachment) = {
    val VideoSites = Set("vimeo.com", "www.vimeo.com", "youtube.com", "www.youtube.com")
    val mime = MIMEType.fromFilename(a.name).getOrElse(MIMEType.OctetStream)
    if (MIMEType.VideoAll.includes(mime) || VideoSites.contains(a.href.getHost)) { //TODO: Hack to allow for broken file names.
      "enclosure video"
    }
    else {
      "enclosure presentation"
    }
  }


  private def toValue(any: Any): Value[_] = any match {
    case x: String => StringValue(x)
    case x: Int => NumberValue(BigDecimal(x))
    case x: Long => NumberValue(BigDecimal(x))
    case x: Double => NumberValue(BigDecimal(x))
    case x: Boolean => BooleanValue(x)
    case null => NullValue
    case _ => throw new IllegalArgumentException("Unknown value " + any.getClass)
  }
}