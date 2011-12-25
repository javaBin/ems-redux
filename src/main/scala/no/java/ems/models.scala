package no.java.ems

import javax.activation.MimeType
import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.util.{Locale}
import org.joda.time.{DateTime, Duration}

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 10/26/11
 * Time: 11:12 PM
 * To change this template use File | Settings | File Templates.
 */

case class Event(id: Option[String], title: String, start: DateTime, end: DateTime) {
  require(start.isBefore(end), "Start must be before End")
}

case class SessionAbstract(title: String,
                           lead: Option[String] = None,
                           body: Option[String] = None,
                           language: Locale = new Locale("no"),
                           level: Level = Level.Beginner,
                           format: Format = Format.Presentation,
                           speakers: Vector[Speaker] = Vector()
                            ) {
  def addSpeaker(speaker: Speaker) = copy(speakers = speakers :+ speaker)

  def withTitle(input: String) = copy(input)

  def withBody(input: String) = copy(body = Some(input))

  def withLead(input: String) = copy(lead = Some(input))

  def withFormat(format: Format) = copy(format = format)

  def withLevel(level: Level) = copy(level = level)

}

case class Session(id: Option[String],
                   eventId: String,
                   duration: Duration,
                   sessionAbstract: SessionAbstract,
                   state: State,
                   published: Boolean,
                   attachments: List[URIAttachment],
                   tags: Set[Tag],
                   keywords: Set[Keyword]) {

  def addKeyword(word: String) = copy(keywords = keywords + Keyword(word))

  def addTag(word: String) = copy(tags = tags + Tag(word))

  def withTitle(input: String) = withAbstract(sessionAbstract.withTitle(input))

  def withBody(input: String) = withAbstract(sessionAbstract.withBody(input))

  def withLead(input: String) = withAbstract(sessionAbstract.withLead(input))

  def publish = if (published) this else copy(published = true)

  def withFormat(format: Format) = {
    val duration = format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    copy(sessionAbstract = sessionAbstract.withFormat(format), duration = duration)
  }

  def withLevel(level: Level) = withAbstract(sessionAbstract.withLevel(level))

  def addSpeaker(speaker: Speaker) = withAbstract(sessionAbstract.addSpeaker(speaker))

  def withAbstract(sessionAbstract: SessionAbstract) = copy(sessionAbstract = sessionAbstract)
}

object Session {
  def apply(eventId: String, sessionAbstract: SessionAbstract): Session = {
    val duration = sessionAbstract.format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    Session(None, eventId, duration, sessionAbstract, State.Pending, false, Nil, Set(), Set())
  }

  def apply(eventId: String, sessionAbstract: SessionAbstract, state: State, tags: Set[Tag], keywords: Set[Keyword]): Session = {
    val duration = sessionAbstract.format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    Session(None, eventId, duration, sessionAbstract, state, false, Nil, tags, keywords)
  }

  def apply(eventId: String, title: String, format: Format = Format.Presentation, speakers: Vector[Speaker] = Vector()): Session = {
    val duration = format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    val ab = SessionAbstract(title, format = format, speakers = speakers)
    Session(None, eventId, duration, ab, State.Pending, false, Nil, Set(), Set())
  }
}

case class Speaker(contactId: String, name: String, bio: String, image: Attachment)

case class Contact(id: Option[String], name: String, foreign: Boolean, bio: String, image: Attachment, emails: List[Email])

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

case class MIMEType(major: String, minor: String, parameters: Map[String, String] = Map.empty)

object MIMEType {
  val ALL = apply("*/*")
  val IMAGE_ALL = apply("image/*")
  val VIDEO_ALL = apply("video/*")
  val PDF = apply("application/pdf")
  val PNG = apply("image/png")
  val JPEG = apply("image/jpeg")

  def apply(mimeType: String): MIMEType = {
    val mime = new MimeType(mimeType)
    import collection.JavaConverters._
    val keys = mime.getParameters.getNames.asInstanceOf[java.util.Enumeration[String]].asScala
    val params = keys.foldLeft(Map[String, String]())((a, b) => a.updated(b, mime.getParameters.get(b)))
    MIMEType(mime.getPrimaryType, mime.getSubType, params)
  }
}


abstract class Attachment(name: String, size: Long, mediaType: MIMEType) {
  def data: InputStream
}

case class ByteArrayAttachment(name: String, size: Long, mediaType: MIMEType, bytes: Array[Byte]) extends Attachment(name, size, mediaType) {
  val data = new ByteArrayInputStream(bytes)
}

case class URIAttachment(name: String, size: Long, mediaType: MIMEType, uri: URI) extends Attachment(name, size, mediaType) {
  def data = uri.toURL.openStream()
}

case class Email(address: String)

case class Tag(name: String)

case class Keyword(name: String)