package ems
package storage

import java.net.URI
import java.sql.Timestamp
import java.util.Locale

import doobie.imports._
import ems.model._
import ems.security.User
import org.joda.time.{Minutes, Duration, DateTime}
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

  implicit val dateTimeMeta: Meta[DateTime] = Meta[Timestamp].xmap(d => new DateTime(d), t => new Timestamp(t.getMillis))
  implicit val durationMeta: Meta[Duration] = Meta[Long].xmap(d => Minutes.minutes(d.toInt).toStandardDuration, t => t.getStandardMinutes)
  implicit val stateMeta: Meta[State] = Meta[String].xmap(v => State(v), _.name)
  implicit val abstractMeta: Meta[Abstract] = codecMeta[Abstract]

  override def binary: BinaryStorage = binaryStorage

  override def shutdown(): Unit = ()

  override def getEvents() = {
    val query = sql"select e.id,e.name,e.venue,e.slug,e.lastmodified".query[Event].vector
    xa.trans(query).run
  }

  override def getEventsWithSessionCount(user: User) = {
    val auth = if (user.authenticated) "" else "and published = true"
    val withSessions = sql"select e.id,e.name,e.venue,e.slug,e.lastmodified,count(s.*) as sessionCount from event e left join session s on s.eventId = e.id group $auth by e.id".query[(Event, Long)].vector
    val q = withSessions.map(_.map{case (e, count) => EventWithSessionCount(e, count.toInt)})
    xa.trans(q).run
  }

  override def getEventsBySlug(name: String): Vector[Event] = {
    val query = sql"select id,name,venue,slug,lastModified from event where slug = $name".query[Event].vector
    xa.trans(query).run
  }

  override def getEvent(id: UUID): Option[Event] = {
    val query = sql"select id,name,venue,slug,lastModified from event where id = $id".query[Event].option
    xa.trans(query).run
  }

  override def saveEvent(event: Event): Either[Throwable, Event] = ???

  override def getRooms(eventId: UUID): Vector[Room] = {
    val query = sql"select id,eventId,name,lastModified from room where eventId = $eventId".query[Room].vector
    xa.trans(query).run
  }

  override def getRoom(eventId: UUID, id: UUID): Option[Room] = {
    val query = sql"select id,eventId,name,lastModified from room where id = $id and eventId = $eventId".query[Room].option
    xa.trans(query).run
  }

  override def saveRoom(eventId: UUID, room: Room): Either[Throwable, Room] = ???

  override def removeRoom(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getSlot(eventId: UUID, id: UUID): Option[Slot] = {
    val query = sql"select id,eventId,start,duration,parentId,lastModified from slot where id = $id and eventId = $eventId".query[Slot].option
    xa.trans(query).run
  }

  override def getSlots(eventId: UUID, parent: Option[UUID]): Vector[Slot] = {
    val parentSql = if (parent.isDefined) s"and parentId = '${parent.get.toString}'" else ""
    val query = sql"select id,eventId,start,duration,parentId,lastModified from slot where eventId = $eventId $parentSql".query[Slot].vector
    xa.trans(query).run
  }

  override def saveSlot(slot: Slot): Either[Throwable, Slot] = ???

  override def removeSlot(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getSessions(eventId: UUID)(user: User): Vector[Session] = {
    val published = if (user.authenticated) "" else "AND published=false"
    val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId".query[Session].vector
    xa.trans(query).run
  }

  override def getSessionsBySlug(eventId: UUID, slug: String): Vector[Session] = {
    val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId and slug = $slug".query[Session].vector
    xa.trans(query).run
  }

  override def getSession(eventId: UUID, id: UUID): Option[Session] = {
    val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId and id = $id".query[Session].option
    xa.trans(query).run
  }

  override def getChangedSessions(eventId: UUID, from: DateTime)(implicit u: User): Vector[Session] = {
    val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId and lastModified > $from".query[Session].vector
    xa.trans(query).run
  }

  override def publishSessions(eventId: UUID, sessions: Seq[UUID]): Either[Throwable, Unit] = ???

  override def saveSlotInSession(eventId: UUID, sessionId: UUID, slot: Slot): Either[Throwable, Session] = ???

  override def saveRoomInSession(eventId: UUID, sessionId: UUID, room: Room): Either[Throwable, Session] = ???

  override def saveSession(session: Session): Either[Throwable, Session] = ???

  override def removeSession(sessionId: UUID): Either[Throwable, Unit] = ???

  override def saveAttachment(eventId: UUID, sessionId: UUID, attachment: URIAttachment): Either[Throwable, Unit] = ???

  override def status(): String = "OK"

  override def removeAttachment(eventId: UUID, sessionId: UUID, id: UUID): Either[Throwable, Unit] = ???

  override def removeSpeaker(sessionId: UUID, speakerId: UUID): Either[Throwable, Unit] = ???

  override def saveSpeaker(sessionId: UUID,speaker: Speaker): Either[Throwable, Speaker] = ???

  override def getSpeaker(sessionId: UUID, speakerId: UUID): Option[Speaker] = ???

  override def getSpeakers(sessionId: UUID): Vector[Speaker] = ???

  override def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: Attachment with Entity[Attachment]): Either[Throwable, Unit] = ???

}
