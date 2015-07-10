package ems.storage

import java.net.URI
import java.sql.Timestamp
import java.util.Locale

import doobie.imports._
import ems.{MIMEType, URIAttachment, Attachment}
import ems.model._
import ems.security.User
import org.joda.time.DateTime
import org.postgresql.util.PGobject
import scalaz.concurrent.Task
import argonaut._, Argonaut._
import scala.reflect.runtime.universe.TypeTag
import scalaz.syntax.id._

object Codecs {
  implicit val dateTimeCodec: CodecJson[DateTime] = CodecJson.derived[String].xmap(DateTime.parse)(_.toString())
  implicit val levelCodec: CodecJson[Level] = CodecJson.derived[String].xmap(Level(_))(_.name)
  implicit val uriCodec: CodecJson[URI] = CodecJson.derived[String].xmap(URI.create)(_.toString)
  implicit val mimeCodec: CodecJson[MIMEType] = CodecJson.derived[String].xmap(s => MIMEType(s).getOrElse(MIMEType
  .OctetStream))(_.toString)
  implicit val formatCodec: CodecJson[Format]  = CodecJson.derived[String].xmap(Format(_))(_.name)
  implicit val keywordCodec: CodecJson[Keyword]  = CodecJson.derived[String].xmap(Keyword)(_.name)
  implicit val tagCodec: CodecJson[Tag]  = CodecJson.derived[String].xmap(Tag)(_.name)
  implicit val localeCodec: CodecJson[Locale]  = CodecJson.derived[String].xmap(new Locale(_))(_.getLanguage)
  implicit val attachmentCodec: CodecJson[URIAttachment] = casecodec6(URIAttachment.apply, URIAttachment.unapply)(
    "id",
    "href",
    "name",
    "size",
    "media-type",
    "last-modified"
  )

  implicit val abstractCodec = casecodec12(Abstract.apply, Abstract.unapply)(
    "title",
    "summary",
    "body",
    "audience",
    "outline",
    "equipment",
    "language",
    "level",
    "format",
    "tags",
    "keywords",
    "attachments"
  )
}

case class InternalSession(id: Option[String],
                           eventId: String,
                           slug: String,
                           roomId: Option[String],
                           slotId: Option[String],
                           abs: Abstract,
                           state: State,
                           published: Boolean,
                           lastModified: DateTime = new DateTime())


class SQLStorage(xa: Transactor[Task], binaryStorage: BinaryStorage) extends DBStorage {
  import Codecs._

  implicit val JsonMeta: Meta[Json] = 
  Meta.other[PGobject]("jsonb").nxmap[Json](
    a => Parse.parse(a.getValue).leftMap[Json](sys.error).merge, // failure raises an exception
    a => new PGobject <| (_.setType("jsonb")) <| (_.setValue(a.nospaces))
  )

  def codecMeta[A >: Null : CodecJson: TypeTag]: Meta[A] =
  Meta[Json].nxmap[A](
    _.as[A].result.fold(p => sys.error(p._1), identity), 
    _.asJson
  )

  implicit val jodaTimeMeta: Meta[DateTime] = Meta[Timestamp].xmap(d => new DateTime(d), t => new Timestamp(t.getMillis))
  implicit val stateMeta: Meta[State] = Meta[String].xmap(v => State(v), _.name)
  implicit val abstractMeta: Meta[Abstract] = codecMeta[Abstract]

  def getEvents() = {
    val query = sql"select id,name,venue,lastModified from event".query[Event].process
    xa.transP(query).vector.run
  }

  def getEventsWithSessionCount(user: User) = ???

  override def binary: BinaryStorage = binaryStorage

  override def publishSessions(eventId: String, sessions: Seq[String]): Either[Throwable, Unit] = ???

  override def saveSession(session: Session): Either[Throwable, Session] = ???

  override def saveAttachment(eventId: String, sessionId: String, attachment: URIAttachment): Either[Throwable, Unit] = ???

  override def getRooms(eventId: String): Vector[Room] = ???

  override def saveSpeaker(eventId: String, sessionId: String, speaker: Speaker): Either[Throwable, Speaker] = ???

  override def getSessions(eventId: String)(user: User): Vector[Session] = {
    val published = if (user.authenticated) "" else "AND published=false"
    val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId".query[InternalSession].process
    xa.transP(query).vector.run
  }

  override def shutdown(): Unit = ???

  override def getRoom(eventId: String, id: String): Option[Room] = ???

  override def removeSlot(eventId: String, id: String): Either[Throwable, String] = ???

  override def getSlot(id: String): Option[Slot] = ???

  override def getSlots(eventId: String, parent: Option[String]): Vector[Slot] = ???

  override def getSessionsBySlug(eventId: String, slug: String): Vector[Session] = ???

  override def saveRoom(eventId: String, room: Room): Either[Throwable, Room] = ???

  override def getChangedSessions(from: DateTime)(implicit u: User): Vector[Session] = ???

  override def getEventsBySlug(name: String): Vector[Event] = ???

  override def getSessionCount(eventId: String)(user: User): Int = ???

  override def getEvent(id: String): Option[Event] = ???

  override def saveSlotInSession(eventId: String, sessionId: String, slot: Slot): Either[Throwable, Session] = ???

  override def removeSession(sessionId: String): Either[Throwable, Unit] = ???

  override def status(): String = ???

  override def removeAttachment(eventId: String, sessionId: String, id: String): Either[Throwable, Unit] = ???

  override def getSession(eventId: String, id: String): Option[Session] = ???

  override def removeSpeaker(eventId: String, sessionId: String, speakerId: String): Either[Throwable, Unit] = ???

  override def saveRoomInSession(eventId: String, sessionId: String, room: Room): Either[Throwable, Session] = ???

  override def saveSlot(slot: Slot): Either[Throwable, Slot] = ???

  override def getSpeaker(eventId: String, sessionId: String, speakerId: String): Option[Speaker] = ???

  override def saveEvent(event: Event): Either[Throwable, Event] = ???

  override def updateSpeakerWithPhoto(eventId: String, sessionId: String, speakerId: String, photo: Attachment with Entity[Attachment]): Either[Throwable, Unit] = ???

  override def removeRoom(eventId: String, id: String): Either[Throwable, String] = ???

  override def getAllSlots(eventId: String): Vector[SlotTree] = ???
}
