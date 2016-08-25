package ems

import java.net.URI
import model._
import net.hamnaberg.json.collection._
import ems.util.URIBuilder
import ems.util.RFC3339
import java.util.Locale
import net.hamnaberg.json.collection.Value._
import security.User
import org.joda.time.{Duration, Minutes}
import scravatar.Gravatar

import scala.util.Properties

object converters {
  val permalinks: SessionPermalinks = SessionPermalinks.fromEnvironment(Properties.propOrElse("CONSTRETTO_TAGS", "dev"))

  def eventToItem(baseBuilder: URIBuilder): (Event) => Item = {
    e => {
      val properties = Map(
        "name" -> Some(e.name),
        "slug" -> Some(e.slug),
        "venue" -> Some(e.venue)
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", e.id.get)
      val links = List(
        Link(href.segments("sessions").build(), "session collection", Some("Sessions")),
        Link(href.segments("slots").build(), "slot collection", Some("Slots")),
        Link(href.segments("rooms").build(), "room collection", Some("Rooms")),
        Link(href.segments("tags").build(), "tag index", Some("Tags"))
      )
      Item(href.build(), properties, links)
    }
  }

  def roomToItem(baseBuilder: URIBuilder, eventId: UUID): (Room) => Item = {
    r => {
      val properties = Map(
        "name" -> Some(r.name)
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", eventId, "rooms", r.id.get).build()
      Item(href, properties, Nil)
    }
  }

  def slotToItem(baseBuilder: URIBuilder, eventId: UUID): (Slot) => Item = {
    r => {
      val properties = Map(
        "start" -> Some(RFC3339.format(r.start)),
        "duration" -> Some(r.duration),
        "end" -> Some(RFC3339.format(r.start.plus(r.duration)))
      ).map(toProperty).toList
      val href = baseBuilder.segments("events", eventId, "slots", r.id.get).build()
      Item(href, properties,  List(Link(URIBuilder(href).segments("children").build(), "slot collection")))
    }
  }

  def toSlot(template: Template, eventId: UUID, parent: Option[UUID] = None, id: Option[UUID] = None): Slot = Slot(
    id,
    eventId,
    template.getPropertyValue("start").map(v => RFC3339.parseDateTime(v.toString).right.get).get,
    template.getPropertyValue("duration").map(v => Minutes.minutes(v.toString.toInt).toStandardDuration).get,
    parent
  )

  def toRoom(template: Template, eventId: UUID, id: Option[UUID] = None): Room = Room(
    id,
    eventId,
    template.getPropertyValue("name").map(_.toString).get
  )

  def enrichedSessionToItem(baseBuilder: URIBuilder)(implicit u: User): (EnrichedSession) => Item = {
    es => {
      val filtered = toProperties(es.session)
      val href = baseBuilder.segments("events", es.session.eventId, "sessions", es.session.id.get).build()
      Item(href, filtered, createSessionLinks(baseBuilder, href, es))
    }
  }

  private def toProperties(s: Session)(implicit u: User) = {
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
      "published" -> Some(s.published)
    ) ++ handlePrivateProperties(u, s)
    properties.filter{case (k,v) => v.isDefined}.map(toProperty).toList
  }

  def sessionToItem(baseBuilder: URIBuilder)(implicit u: User): (Session) => Item = {
    s => {
      val filtered = toProperties(s)
      val href = baseBuilder.segments("events", s.eventId, "sessions", s.id.get).build()
      Item(href, filtered, createSessionLinks(baseBuilder, href, EnrichedSession(s, None, None, Vector.empty)))
    }
  }


  private def handlePrivateProperties(u: User, s: Session): Seq[(String, Option[Any])] = {
    val map = s.abs.labels.toList.sortBy(_._1)

    if (u.authenticated) {
      val map = s.abs.labels.toList.sortBy(_._1).map{ case (k, v) => k -> Some(v.filterNot(_.isEmpty)) }
      Seq(
        "outline" -> s.abs.outline,
        "equipment" -> s.abs.equipment
      ) ++ map
    }
    else {
      (s.abs.labels - "tags").toList.sortBy(_._1).map{ case (k, v) => k -> Some(v.filterNot(_.isEmpty)) }
    }
  }

  private def createSessionLinks(baseURIBuilder: URIBuilder, href: URI, s: EnrichedSession): List[Link] = {
    val links = List.newBuilder[Link]

    links ++= List(
      Link(URIBuilder(href).segments("attachments").build(), "attachment collection", Some("Attachments")),
      Link(URIBuilder(href).segments("speakers").build(), "speaker collection", Some("Speakers")),
      Link(URIBuilder(href).segments("tags").build(), "session tag", Some("Tag session")),
      Link(URIBuilder(href).segments("slot").build(), "session slot", Some("Assign a slot")),
      Link(URIBuilder(href).segments("room").build(), "session room", Some("Assign a room")),
      Link(baseURIBuilder.segments("events", s.session.eventId, "sessions").build(), "publish", Some("Publish the session"))
    )
    links ++= s.session.video.map(href => Link(href, "alternate video"))
    links ++= s.room.map(r => Link(baseURIBuilder.segments("events", s.session.eventId, "rooms", r.id.get.toString).build(), "room item", Some(r.name))).toSeq
    links ++= s.slot.map(slot => Link(baseURIBuilder.segments("events", s.session.eventId, "slots", slot.id.get.toString).build(), "slot item", Some(formatSlot(slot)))).toSeq
    links ++= s.speakers.map(speaker => Link(URIBuilder(href).segments("speakers", speaker.id.get).build(), "speaker item", Some(speaker.name)))
    links ++= permalinks.expand(s.session, href).map(h => Link(h, "alternate", Some("Permalink"))).toSeq
    links.result()
  }

  def formatSlot(slot: Slot): String = slot.formatted

  def speakerToItem(builder: URIBuilder, eventId: UUID, sessionId: UUID)(implicit user: User): (Speaker) => (Item) = {
    s => {
      val auths = if (user.authenticated) {
        List(
          ValueProperty("email", Some("Email"), Some(StringValue(s.email))),
          ValueProperty("zip-code", Some("Zip Code"), s.zipCode.map(StringValue))
        ) ++ s.labels.toList.sortBy(_._1).map{ case (k, v) => ListProperty(k, None, v.map(StringValue)) }
      } else {
        (s.labels - "tags").toList.sortBy(_._1).map{ case (k, v) => ListProperty(k, None, v.map(StringValue)) }
      }

      val base = builder.segments("events", eventId, "sessions", sessionId, "speakers", s.id.get)
      val data = List(
        ValueProperty("name", Some("Name"), Some(StringValue(s.name))),
        ValueProperty("bio", Some("Bio"), s.bio.map(StringValue))
      ) ++ auths
      val photos = s.photo.map{a =>
        val binary = builder.segments("binary", a.id.get).build()
        List(
          Link(binary, "photo", None, None, Some(Render.IMAGE)),
          Link(URIBuilder(binary).queryParam("size", ImageSize.Thumb.name).build(), "thumbnail", None, Some(ImageSize.Thumb.toString), Some(Render.IMAGE))
        )
      }.getOrElse(List(
        Link(URI.create(Gravatar(s.email).default(scravatar.IdentIcon).size(100).ssl(true).avatarUrl), "thumbnail", None, Some("gravatar"), Some(Render.IMAGE))
      ))
      Item(
        base.build(),
        data,
        photos ++ List(Link(base.segments("photo").build(), "attach-photo"))
      )
    }
  }

  def toEvent(template: Template, id: Option[UUID] = None): Event = {
    val name = template.getPropertyValue("name").map(_.value.toString).get
    val venue = template.getPropertyValue("venue").map(_.value.toString).get
    Event(id, name, Slug.makeSlug(name), venue)
  }

  private def toAbstract(template: Template): Abstract = {
    val title = template.getPropertyValue("title").get.value.toString
    val body = template.getPropertyValue("body").map(_.value.toString)
    val outline = template.getPropertyValue("outline").map(_.value.toString)
    val audience = template.getPropertyValue("audience").map(_.value.toString)
    val equipment = template.getPropertyValue("equipment").map(_.value.toString)
    val summary = template.getPropertyValue("summary").map(_.value.toString)
    val format = template.getPropertyValue("format").map(x => Format(x.value.toString))
    val level = template.getPropertyValue("level").map(x => Level(x.value.toString))
    val language = template.getPropertyValue("lang").map(x => new Locale(x.value.toString))
    val tags = template.getPropertyAsSeq("tags").map(t => t.value.toString).distinct.toList
    val keywords = template.getPropertyAsSeq("keywords").map(k => k.value.toString).distinct.toList

    val map: Map[String, List[String]] = Map(
      "tags" -> tags,
      "keywords" -> keywords
    )
    Abstract(title, summary, body, audience, outline, equipment, language.getOrElse(new Locale("no")), level.getOrElse(Level.Beginner), format.getOrElse(Format.Presentation), map)
  }

  def toSession(eventId: UUID, id: Option[UUID], template: Template): Session = {
    val abs = toAbstract(template)
    val state = template.getPropertyValue("state").map(x => State(x.value.toString))
    val published = template.getPropertyValue("published").exists(x => x.value.toString.toBoolean)
    val sess = Session(eventId, abs, state.getOrElse(State.Pending), published)
    sess.copy(id = id)
  }

  def toSpeaker(template: Template, id: Option[UUID] = None): Speaker = {
    val name = template.getPropertyValue("name").get.value.toString
    val email = template.getPropertyValue("email").get.value.toString
    val bio = template.getPropertyValue("bio").map(_.value.toString)
    val zipCode = template.getPropertyValue("zip-code").map(_.value.toString)
    val tags = template.getPropertyAsSeq("tags").map(t => t.value.toString).toList.distinct
    Speaker(id, name, email, zipCode, bio, Map("tags" -> tags))
  }


  private[ems] def toProperty: PartialFunction[(String, Option[Any]), Property] = {
    case (a, Some(x: Seq[_])) => ListProperty(a, Some(a.capitalize), x.map(toValue))
    case (a, Some(x: Map[_, _])) => ObjectProperty(a, Some(a.capitalize), x.map{case (k: Any, v: Any) => k.toString -> toValue(v)}.toMap)
    case (a, b) => ValueProperty(a, Some(a.capitalize), b.map(toValue))
  }

  private def toValue(any: Any): Value[_] = any match {
    case x: String => StringValue(x)
    case x: Int => NumberValue(BigDecimal(x))
    case x: Long => NumberValue(BigDecimal(x))
    case x: Double => NumberValue(BigDecimal(x))
    case x: Boolean => BooleanValue(x)
    case x: Duration => NumberValue(x.getStandardMinutes)
    case null => NullValue
    case _ => throw new IllegalArgumentException("Unknown value " + any.getClass)
  }
}
