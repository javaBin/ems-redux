package ems.model

import ems.storage.MongoDBStorage
import ems.storage.BinaryStorage
import ems.{Attachment, URIAttachment}
import org.joda.time.DateTime
import com.mongodb.casbah.Imports._
import java.util.{Date => JDate, Locale, UUID}

case class Abstract(title: String,
                    summary: Option[String] = None,
                    body: Option[String] = None,
                    audience: Option[String] = None,
                    outline: Option[String] = None,
                    equipment: Option[String] = None,
                    language: Locale = new Locale("no"),
                    level: Level = Level.Beginner,
                    format: Format = Format.Presentation
                     ) {
  def withTitle(input: String) = copy(input)

  def withBody(input: String) = copy(body = Some(input))

  def withAudience(input: String) = copy(audience = Some(input))

  def withOutline(input: String) = copy(outline = Some(input))

  def withEquipment(input: String) = copy(equipment = Some(input))

  def withSummary(input: String) = copy(summary = Some(input))

  def withFormat(format: Format) = copy(format = format)

  def withLevel(level: Level) = copy(level = level)


  def toMongo = MongoDBObject(
    "title" -> title.trim.noHtml,
    "body" -> body.map(_.noHtml),
    "summary" -> summary.map(_.noHtml),
    "equipment" -> equipment.map(_.noHtml),
    "outline" -> outline.map(_.noHtml),
    "audience" -> audience.map(_.noHtml),
    "format" -> format.name,
    "level" -> level.name,
    "language" -> language.getLanguage
  )
}

object Abstract {
  def apply(dbo: DBObject): Abstract = {
    val m = wrapDBObj(dbo)
    val asString = m.getAs[String] _
    val format = asString("format").map(Format(_)).getOrElse(Format.Presentation)
    val level = asString("level").map(Level(_)).getOrElse(Level.Beginner)
    Abstract(
      m.getAsOrElse("title", "No Title").noHtml,
      asString("summary").map(_.noHtml),
      asString("body").map(_.noHtml),
      asString("audience").map(_.noHtml),
      asString("outline").map(_.noHtml),
      asString("equipment").map(_.noHtml),
      asString("language").map(l => new Locale(l)).getOrElse(new Locale("no")),
      level,
      format
    )
  }

}

case class Session(id: Option[String],
                   eventId: String,
                   slug: String,
                   room: Option[Room],
                   slot: Option[Slot],
                   abs: Abstract,
                   state: State,
                   published: Boolean,
                   tags: Set[Tag],
                   keywords: Set[Keyword],
                   speakers: Seq[Speaker],
                   attachments: Seq[URIAttachment] = Nil,
                   lastModified: DateTime = new DateTime()) extends Entity[Session] {


  type T = Session

  def addAttachment(attachment: URIAttachment) = copy(attachments = attachments ++ Seq(attachment))

  def addKeyword(word: String) = copy(keywords = keywords + Keyword(word))

  def addTag(word: String) = copy(tags = tags + Tag(word))

  def withTitle(input: String) = withAbstract(abs.withTitle(input))

  def withBody(input: String) = withAbstract(abs.withBody(input))

  def withSummary(input: String) = withAbstract(abs.withSummary(input))

  def withRoom(room: Room) = copy(room = Some(room))

  def withSlot(slot: Slot) = copy(slot = Some(slot))

  def withFormat(format: Format) = copy(abs = abs.withFormat(format))

  def withLevel(level: Level) = withAbstract(abs.withLevel(level))

  def withTags(tags: Set[Tag]) = copy(tags = tags)

  def addOrUpdateSpeaker(speaker: Speaker) = {
    val speakers = Vector(this.speakers : _*)
    val index = speakers.indexWhere(_.id == speaker.id)
    if (index != -1) {
      withSpeakers(speakers.updated(index, speaker))
    }
    else {
      addSpeaker(speaker)
    }
  }

  def addSpeaker(speaker: Speaker) = copy(speakers = speakers ++ Seq(speaker))

  def withSpeakers(speakers: Seq[Speaker]) = copy(speakers = speakers)

  def withAbstract(abs: Abstract) = copy(abs = abs)

  def withId(id: String) = copy(id = Some(id))

  def toMongo(update: Boolean): DBObject = {
    val base = MongoDBObject(
      "slug" -> slug,
      "tags" -> tags.map(_.name.noHtml),
      "keywords" -> keywords.map(_.name.noHtml),
      "state" -> state.name,
      "last-modified" -> DateTime.now().toDate
    ) ++ abs.toMongo

    if (update) {
      MongoDBObject(
        "$set" -> base
      )
    }
    else {
      val obj = MongoDBObject(
        "_id" -> id.getOrElse(UUID.randomUUID().toString),
        "eventId" -> eventId,
        "published" -> published,
        "roomId" -> room.flatMap(_.id),
        "slotId" -> slot.flatMap(_.id),
        "attachments" -> attachments.map(_.toMongo),
        "speakers" -> speakers.map(_.toMongo)
      ) ++ base
      obj
    }
  }
}

object Session {
  def apply(eventId: String, abs: Abstract): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title.noHtml), None, None, abs, State.Pending, false, Set(), Set(), Nil)
  }

  def apply(eventId: String, abs: Abstract, state: State, tags: Set[Tag], keywords: Set[Keyword]): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title.noHtml), None, None, abs, state, false, tags, keywords, Nil)
  }

  def apply(eventId: String, title: String, format: Format, speakers: Seq[Speaker]): Session = {
    val ab = Abstract(title, format = format)
    Session(None, eventId, Slug.makeSlug(title.noHtml), None, None, ab, State.Pending, false, Set(), Set(), speakers, Nil)
  }

  def apply(dbo: DBObject, storage: MongoDBStorage): Session = {
    val m = wrapDBObj(dbo)
    val abs = Abstract(dbo)
    val eventId = m.get("eventId").map(_.toString).getOrElse(throw new IllegalArgumentException("No eventId"))
    val event = storage.getEvent(eventId).getOrElse(throw new IllegalArgumentException("No Event"))
    val slot = event.slots.find(_.id == m.get("slotId").map(_.toString))
    val room = event.rooms.find(_.id == m.get("roomId").map(_.toString))
    val speakers = m.getAsOrElse[Seq[_]]("speakers", Seq()).map {
      case x: DBObject => x
    }.map(Speaker(_, storage.binary))
    val attachments = m.getAsOrElse[Seq[_]]("attachments", Seq()).map {
      case x: DBObject => x
    }.map(URIAttachment(_))

    Session(
      m.get("_id").map(_.toString),
      m.get("eventId").map(_.toString).getOrElse(throw new IllegalArgumentException("No eventId")),
      m.get("slug").map(_.toString).get,
      room,
      slot,
      abs,
      m.getAs[String]("state").map(State(_)).getOrElse(State.Pending),
      m.getAs[Boolean]("published").getOrElse(false),
      m.getAsOrElse[Seq[_]]("tags", Seq.empty).map(t => Tag(t.toString)).toSet[Tag],
      m.getAsOrElse[Seq[_]]("keywords", Seq.empty).map(k => Keyword(k.toString)).toSet[Keyword],
      speakers,
      attachments,
      new DateTime(m.getAsOrElse[JDate]("last-modified", new JDate()))
    )
  }

}

case class Speaker(id: Option[String], name: String, email: String, zipCode: Option[String] = None, bio: Option[String] = None, tags: Set[Tag] = Set.empty, photo: Option[Attachment with Entity[Attachment]] = None, lastModified: DateTime = DateTime.now()) extends Entity[Speaker] {
  type T = Speaker

  def withId(id: String) = copy(id = Some(id))

  def toMongo = MongoDBObject(
    "_id" -> id,
    "name" -> name.noHtml,
    "email" -> email,
    "zip-code" -> zipCode,
    "bio" -> bio.map(_.noHtml),
    "tags" -> tags.map(_.name.noHtml),
    "last-modified" -> DateTime.now.toDate
  )
}

object Speaker {
  def apply(dbo: DBObject, binaryStorage: BinaryStorage): Speaker = {
    val m = wrapDBObj(dbo)
    Speaker(
      m.get("_id").map(_.toString),
      m.as[String]("name").noHtml,
      m.as[String]("email"),
      m.getAs[String]("zip-code"),
      m.getAs[String]("bio").map(_.noHtml),
      m.getAsOrElse[Seq[_]]("tags", Seq.empty).map(t => Tag(t.toString)).toSet[Tag],
      m.get("photo").flatMap(i => binaryStorage.getAttachment(i.toString)),
      new DateTime(m.getAs[JDate]("last-modified").getOrElse(new JDate()))
    )
  }
}


sealed abstract class Level(val name: String) {
  override def toString = name
}

object Level {

  def apply(name: String): Level = name match {
    case Level(level) => level
    case _ => Beginner
  }

  def unapply(level: Level): Option[String] = Some(level.name)

  def unapply(f: String): Option[Level] = f match {
    case Beginner.name => Some(Beginner)
    case Beginner_Intermediate.name => Some(Beginner_Intermediate)
    case Intermediate.name => Some(Intermediate)
    case Intermediate_Advanced.name => Some(Intermediate_Advanced)
    case Advanced.name => Some(Advanced)
    case Hardcore.name => Some(Hardcore)
    case _ => None
  }


  case object Beginner extends Level("beginner")

  case object Beginner_Intermediate extends Level("beginner-intermediate")

  case object Intermediate extends Level("intermediate")

  case object Intermediate_Advanced extends Level("intermediate-advanced")

  case object Advanced extends Level("advanced")

  case object Hardcore extends Level("hardcore")

  val values: Seq[Level] = Seq(Beginner, Beginner_Intermediate, Intermediate, Intermediate_Advanced, Advanced, Hardcore)

}

sealed abstract class Format(val name: String) {
  override def toString = name
}

object Format {

  def apply(name: String): Format = name match {
    case Format(n) => n
    case _ => Presentation
  }

  def unapply(f: Format): Option[String] = Some(f.name)

  def unapply(f: String): Option[Format] = f match {
    case Presentation.name => Some(Presentation)
    case LightningTalk.name => Some(LightningTalk)
    case Panel.name => Some(Panel)
    case BoF.name => Some(BoF)
    case Workshop.name => Some(Workshop)
    case _ => None
  }


  case object Presentation extends Format("presentation")

  case object Workshop extends Format("workshop")

  case object LightningTalk extends Format("lightning-talk")

  case object Panel extends Format("panel")

  case object BoF extends Format("bof")


  val values: Seq[Format] = Seq(Presentation, LightningTalk, Panel, BoF)
}

sealed abstract class State(val name: String) {
  override def toString = name
}

object State {

  def apply(name: String): State = name match {
    case State(n) => n
    case _ => Pending
  }

  def unapply(f: State): Option[String] = Some(f.name)

  def unapply(f: String): Option[State] = f match {
    case Rejected.name => Some(Rejected)
    case Approved.name => Some(Approved)
    case Pending.name => Some(Pending)
    case _ => None
  }

  case object Rejected extends State("rejected")

  case object Approved extends State("approved")

  case object Pending extends State("pending")

  val values: Seq[State] = Seq(Pending, Rejected, Approved)

}