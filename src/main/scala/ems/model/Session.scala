package ems
package model

import org.joda.time.DateTime
import java.util.Locale

case class Abstract(title: String,
                    summary: Option[String] = None,
                    body: Option[String] = None,
                    audience: Option[String] = None,
                    outline: Option[String] = None,
                    equipment: Option[String] = None,
                    language: Locale = new Locale("no"),
                    level: Level = Level.Beginner,
                    format: Format = Format.Presentation,
                    keywords: Set[Keyword],
                    tags: Set[Tag],
                    attachments: Seq[URIAttachment] = Nil
                     ) {
  def withTitle(input: String) = copy(input)

  def withBody(input: String) = copy(body = Some(input))

  def withAudience(input: String) = copy(audience = Some(input))

  def withOutline(input: String) = copy(outline = Some(input))

  def withEquipment(input: String) = copy(equipment = Some(input))

  def withSummary(input: String) = copy(summary = Some(input))

  def withFormat(format: Format) = copy(format = format)

  def withLevel(level: Level) = copy(level = level)

  def addAttachment(attachment: URIAttachment) = copy(attachments = attachments ++ Seq(attachment))

  def addKeyword(word: String) = copy(keywords = keywords + Keyword(word))

  def addTag(word: String) = copy(tags = tags + Tag(word))

  def withTags(tags: Set[Tag]) = copy(tags = tags)

}

case class Session(id: Option[UUID],
                   eventId: UUID,
                   slug: String,
                   room: Option[UUID],
                   slot: Option[UUID],
                   abs: Abstract,
                   state: State,
                   published: Boolean,
                   lastModified: DateTime = new DateTime()) extends Entity[Session] {

  type T = Session


  def withTitle(input: String) = withAbstract(abs.withTitle(input))

  def withBody(input: String) = withAbstract(abs.withBody(input))

  def withSummary(input: String) = withAbstract(abs.withSummary(input))

  def withRoom(room: UUID) = copy(room = Some(room))

  def withSlot(slot: UUID) = copy(slot = Some(slot))

  def withFormat(format: Format) = withAbstract(abs.withFormat(format))

  def withLevel(level: Level) = withAbstract(abs.withLevel(level))

  def withTags(tags: Set[Tag]) = withAbstract(abs.withTags(tags))

  def withAbstract(abs: Abstract) = copy(abs = abs)

  def withId(id: UUID) = copy(id = Some(id))
}

object Session {
  def apply(eventId: UUID, abs: Abstract): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title.noHtml), None, None, abs, State.Pending, false)
  }

  def apply(eventId: UUID, abs: Abstract, state: State): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title.noHtml), None, None, abs, state, false)
  }

  def apply(eventId: UUID, abs: Abstract, state: State, published: Boolean): Session = {
    Session(None, eventId, Slug.makeSlug(abs.title.noHtml), None, None, abs, state, published)
  }

  def apply(eventId: UUID, title: String, format: Format): Session = {
    val ab = Abstract(title, format = format, tags = Set.empty, keywords = Set.empty)
    Session(None, eventId, Slug.makeSlug(title.noHtml), None, None, ab, State.Pending, false)
  }
}

case class EnrichedSession(session: Session, room: Option[Room], slot: Option[Slot], speakers: Vector[Speaker])

case class Speaker(id: Option[UUID], name: String, email: String, zipCode: Option[String] = None, bio: Option[String] = None, tags: Set[Tag] = Set.empty, photo: Option[Attachment with Entity[Attachment]] = None, lastModified: DateTime = DateTime.now()) extends Entity[Speaker] {
  type T = Speaker

  def withId(id: UUID) = copy(id = Some(id))
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
    case ThunderTalk.name => Some(ThunderTalk)
    case _ => None
  }


  case object Presentation extends Format("presentation")

  case object Workshop extends Format("workshop")

  case object LightningTalk extends Format("lightning-talk")

  case object ThunderTalk extends Format("thunder-talk")

  case object Panel extends Format("panel")

  case object BoF extends Format("bof")

  val values: Seq[Format] = Seq(Presentation, LightningTalk, Workshop, ThunderTalk, Panel, BoF)
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
