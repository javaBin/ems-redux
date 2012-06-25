package no.java.ems

import javax.activation.MimeType
import java.net.URI
import java.util.{Locale}
import org.joda.time.{DateTime, Duration}
import java.io._
import scala.Some
import com.mongodb.casbah.gridfs.{GridFSDBFile, GridFSFile}

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 10/26/11
 * Time: 11:12 PM
 * To change this template use File | Settings | File Templates.
 */

trait Entity {
  type T <: Entity
  def id: Option[String]
  def lastModified: DateTime

  def withId(id: String): T
}

case class Event(id: Option[String], name: String, start: DateTime, end: DateTime, lastModified: DateTime = new DateTime()) extends Entity {
  require(start.isBefore(end), "Start must be before End")


  type T = Event

  def withId(id: String) = copy(id = Some(id))
}

case class Abstract(title: String,
                           lead: Option[String] = None,
                           body: Option[String] = None,
                           language: Locale = new Locale("no"),
                           level: Level = Level.Beginner,
                           format: Format = Format.Presentation,
                           speakers: Vector[Speaker] = Vector()
                            ) {
  def addSpeaker(speaker: Speaker) = copy(speakers = speakers :+ speaker)
  
  def withSpeakers(speakers: Seq[Speaker]) = copy(speakers = Vector() ++ speakers)

  def withTitle(input: String) = copy(input)

  def withBody(input: String) = copy(body = Some(input))

  def withLead(input: String) = copy(lead = Some(input))

  def withFormat(format: Format) = copy(format = format)

  def withLevel(level: Level) = copy(level = level)

}

case class Session(id: Option[String],
                   eventId: String,
                   duration: Option[Duration],
                   abs: Abstract,
                   state: State,
                   published: Boolean,
                   attachments: List[URIAttachment],
                   tags: Set[Tag],
                   keywords: Set[Keyword],
                   lastModified: DateTime = new DateTime()) extends Entity {


  type T = Session

  def addAttachment(attachment: URIAttachment) = copy(attachments = attachments ::: List(attachment))

  def addKeyword(word: String) = copy(keywords = keywords + Keyword(word))

  def addTag(word: String) = copy(tags = tags + Tag(word))

  def withTitle(input: String) = withAbstract(abs.withTitle(input))

  def withBody(input: String) = withAbstract(abs.withBody(input))

  def withLead(input: String) = withAbstract(abs.withLead(input))

  def publish = if (published) this else copy(published = true)

  def withFormat(format: Format) = {
    val duration = format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    copy(abs = abs.withFormat(format), duration = Some(duration))
  }

  def withLevel(level: Level) = withAbstract(abs.withLevel(level))

  def addOrUpdateSpeaker(speaker: Speaker) = {
    val speakers = this.speakers
    val index = speakers.indexWhere(_.contactId == speaker.contactId)
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
    val duration = abs.format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    Session(None, eventId, Some(duration), abs, State.Pending, false, Nil, Set(), Set())
  }

  def apply(eventId: String, abs: Abstract, state: State, tags: Set[Tag], keywords: Set[Keyword]): Session = {
    val duration = abs.format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    Session(None, eventId, Some(duration), abs, state, false, Nil, tags, keywords)
  }

  def apply(eventId: String, title: String, format: Format, speakers: Vector[Speaker]): Session = {
    val duration = format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    val ab = Abstract(title, format = format, speakers = speakers)
    Session(None, eventId, Some(duration), ab, State.Pending, false, Nil, Set(), Set())
  }
}

case class Speaker(contactId: String, name: String, bio: Option[String] = None, photo: Option[Attachment with Entity] = None)

case class Contact(id: Option[String], name: String, foreign: Boolean, bio: Option[String], emails: List[Email], photo: Option[Attachment with Entity] = None, lastModified: DateTime = new DateTime()) extends Entity {

  type T = Contact

  def withId(id: String) = copy(id = Some(id))
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
    case _ => None
  }


  object Beginner extends Level("beginner")

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



case class Email(address: String)

case class Tag(name: String) {
  require(!name.contains(","), "Tag must NOT contain any commas")
}

case class Keyword(name: String) {
  require(!name.contains(","), "Keyword must NOT contain any commas")
}