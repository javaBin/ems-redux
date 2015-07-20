package ems
package storage

import java.net.URI
import java.sql.Timestamp
import java.util.Locale

import doobie.imports._
import ems.model._
import ems.security.User
import org.joda.time.DateTime
import org.postgresql.util.PGobject
import scalaz.concurrent.Task
import argonaut._, Argonaut._
import scala.reflect.runtime.universe.TypeTag
import scalaz.syntax.id._
import doobie.contrib.postgresql.pgtypes._

object Codecs {
  implicit val uuidCodec: CodecJson[UUID] = CodecJson.derived[String].xmap(UUIDFromString)(UUIDToString)
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


  object Queries {
    val events: Query0[Event] = sql"select id,name,venue,lastModified from event".query[Event]
    def numSessions(eventId: String): Query0[Long] = sql"select count(*) from session where eventId = $eventId".query[Long]
  }

  def getEvents() = {
    val query = Queries.events.vector
    xa.trans(query).run
  }

  def getEventsWithSessionCount(user: User) = {
    ???

  }

  override def binary: BinaryStorage = binaryStorage

  override def publishSessions(eventId: UUID, sessions: Seq[UUID]): Either[Throwable, Unit] = ???

  override def saveSession(session: Session): Either[Throwable, Session] = ???

  override def saveAttachment(eventId: UUID, sessionId: UUID, attachment: URIAttachment): Either[Throwable, Unit] = ???

  override def getRooms(eventId: UUID): Vector[Room] = ???

  override def saveSpeaker(eventId: UUID, sessionId: UUID, speaker: Speaker): Either[Throwable, Speaker] = ???

  override def getSessions(eventId: UUID)(user: User): Vector[Session] = {
    val published = if (user.authenticated) "" else "AND published=false"
    val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId".query[Session].vector
    xa.trans(query).run
  }

  override def shutdown(): Unit = ???

  override def getRoom(eventId: UUID, id: UUID): Option[Room] = ???

  override def removeSlot(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getSlot(id: UUID): Option[Slot] = ???

  override def getSlots(eventId: UUID, parent: Option[UUID]): Vector[Slot] = ???

  override def getSessionsBySlug(eventId: UUID, slug: String): Vector[Session] = ???

  override def saveRoom(eventId: UUID, room: Room): Either[Throwable, Room] = ???

  override def getChangedSessions(from: DateTime)(implicit u: User): Vector[Session] = ???

  override def getEventsBySlug(name: String): Vector[Event] = ???

  override def getSessionCount(eventId: UUID)(user: User): Int = ???

  override def getEvent(id: UUID): Option[Event] = ???

  override def saveSlotInSession(eventId: UUID, sessionId: UUID, slot: Slot): Either[Throwable, Session] = ???

  override def removeSession(sessionId: UUID): Either[Throwable, Unit] = ???

  override def status(): String = ???

  override def removeAttachment(eventId: UUID, sessionId: UUID, id: UUID): Either[Throwable, Unit] = ???

  override def getSession(eventId: UUID, id: UUID): Option[Session] = ???

  override def removeSpeaker(eventId: UUID, sessionId: UUID, speakerId: UUID): Either[Throwable, Unit] = ???

  override def saveRoomInSession(eventId: UUID, sessionId: UUID, room: Room): Either[Throwable, Session] = ???

  override def saveSlot(slot: Slot): Either[Throwable, Slot] = ???

  override def getSpeaker(eventId: UUID, sessionId: UUID, speakerId: UUID): Option[Speaker] = ???

  override def getSpeakers(eventId: UUID, sessionId: UUID): Vector[Speaker] = ???

  override def saveEvent(event: Event): Either[Throwable, Event] = ???

  override def updateSpeakerWithPhoto(eventId: UUID, sessionId: UUID, speakerId: UUID, photo: Attachment with Entity[Attachment]): Either[Throwable, Unit] = ???

  override def removeRoom(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getAllSlots(eventId: UUID): Vector[SlotTree] = ???
}
