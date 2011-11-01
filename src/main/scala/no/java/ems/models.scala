package no.java.ems

import java.util.concurrent.TimeUnit
import javax.activation.MimeType
import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.util.{Locale, Date => JDate}
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

case class Session(id: Option[String], eventId: String, title: String, lead: Option[String], body: Option[String], duration: Duration, language: Locale, level : Level, format: Format, speakers: List[Speaker], attachments: List[Attachment], tags: Set[Tag], keywords: Set[Keyword])

object Session {
  def apply(eventId: String,  title: String, format: Format = Format.Presentation, level : Level = Level.Beginner): Session = {
    val duration = format match {
      case Format.LightningTalk => Duration.standardMinutes(10)
      case x => Duration.standardMinutes(60)
    }
    apply(None, eventId, title, None, None, duration, new Locale("no"), level, format, Nil, Nil, Set(), Set())
  }
}

case class Speaker(contactId: String, name: String, bio: String, image: Attachment)

case class Contact(id: Option[String], name: String, foreign: Boolean, bio: String, image: Attachment, emails: List[Email])

sealed trait Level

object Level {
  case object Beginner extends Level
  case object Beginner_Intermediate extends Level
  case object Intermediate extends Level
  case object Intermediate_Advanced extends Level
  case object Advanced extends Level
}

sealed trait Format

object Format {
  case object Presentation extends Format
  case object LightningTalk extends Format
  case object Panel extends Format
  case object BoF extends Format
}

sealed trait State

object State {
  case object Rejected extends State
  case object Approved extends State
  case object Pending extends State
}

case class MIMEType(major: String,  minor: String, parameters: Map[String, String] = Map.empty)

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
    val params = keys.foldLeft(Map[String, String]())((a,b) => a.updated(b, mime.getParameters.get(b)))
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