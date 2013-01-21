package no.java.ems.model

import java.util.Locale
import org.joda.time.{DateTime, Duration}
import scala.Some
import no.java.ems.{Attachment, URIAttachment}

case class Abstract(title: String,
                    summary: Option[String] = None,
                    body: Option[String] = None,
                    audience: Option[String] = None,
                    outline: Option[String] = None,
                    equipment: Option[String] = None,
                    language: Locale = new Locale("no"),
                    level: Level = Level.Beginner,
                    format: Format = Format.Presentation,
                    speakers: Seq[Speaker] = Seq()
                     ) {
  def addSpeaker(speaker: Speaker) = copy(speakers = speakers ++ Seq(speaker))

  def withSpeakers(speakers: Seq[Speaker]) = copy(speakers = speakers)

  def withTitle(input: String) = copy(input)

  def withBody(input: String) = copy(body = Some(input))

  def withAudience(input: String) = copy(audience = Some(input))

  def withOutline(input: String) = copy(outline = Some(input))

  def withEquipment(input: String) = copy(equipment = Some(input))

  def withSummary(input: String) = copy(summary = Some(input))

  def withFormat(format: Format) = copy(format = format)

  def withLevel(level: Level) = copy(level = level)

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
                   attachments: List[URIAttachment] = Nil,
                   lastModified: DateTime = new DateTime()) extends Entity {


  type T = Session

  def addAttachment(attachment: URIAttachment) = copy(attachments = attachments ::: List(attachment))

  def addKeyword(word: String) = copy(keywords = keywords + Keyword(word))

  def addTag(word: String) = copy(tags = tags + Tag(word))

  def withTitle(input: String) = withAbstract(abs.withTitle(input))

  def withBody(input: String) = withAbstract(abs.withBody(input))

  def withSummary(input: String) = withAbstract(abs.withSummary(input))

  def withRoom(room: Room) = copy(room = Some(room))

  def withSlot(slot: Slot) = copy(slot = Some(slot))

  def publish = if (published) this else copy(published = true)

  def withFormat(format: Format) = copy(abs = abs.withFormat(format))

  def withLevel(level: Level) = withAbstract(abs.withLevel(level))

  def addOrUpdateSpeaker(speaker: Speaker) = {
    val speakers = Vector(this.speakers : _*)
    val index = speakers.indexWhere(_.id == speaker.id)
    if (index != -1) {
      withAbstract(abs.withSpeakers(speakers.updated(index, speaker)))
    }
    else {
      withAbstract(abs.addSpeaker(speaker))
    }
  }

  def speakers = abs.speakers

  private def withAbstract(abs: Abstract) = copy(abs = abs)

  def withId(id: String) = copy(id = Some(id))

}

object Session {
  def apply(eventId: String, abs: Abstract): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title), None, None, abs, State.Pending, false, Set(), Set(), Nil)
  }

  def apply(eventId: String, abs: Abstract, state: State, tags: Set[Tag], keywords: Set[Keyword]): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title), None, None, abs, state, false, tags, keywords, Nil)
  }

  def apply(eventId: String, title: String, format: Format, speakers: Vector[Speaker]): Session = {
    val ab = Abstract(title, format = format, speakers = speakers)
    Session(None, eventId, Slug.makeSlug(title), None, None, ab, State.Pending, false, Set(), Set(), Nil)
  }
}

case class Speaker(id: String, name: String, email: String, bio: Option[String] = None, tags: Set[Tag] = Set.empty, photo: Option[Attachment with Entity] = None)


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
    case _ => None
  }


  case object Beginner extends Level("beginner")

  case object Beginner_Intermediate extends Level("beginner-intermediate")

  case object Intermediate extends Level("intermediate")

  case object Intermediate_Advanced extends Level("intermediate-advanced")

  case object Advanced extends Level("advanced")

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
    case _ => None
  }


  case object Presentation extends Format("presentation")

  case object LightningTalk extends Format("lightning-talk")

  case object Panel extends Format("panel")

  case object BoF extends Format("bof")

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

}