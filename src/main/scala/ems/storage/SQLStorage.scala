package ems
package storage

import java.net.URI
import java.util.Locale

import ems.model._
import ems.security.User
import org.joda.time.{Minutes, Duration, DateTime}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import argonaut._, Argonaut._
import slick.driver.PostgresDriver.api._
import scala.concurrent.ExecutionContext.Implicits.global

object Codecs {
  implicit val uuidCodec: CodecJson[UUID] = CodecJson.derived[String].xmap(UUIDFromString)(UUIDToString)
  implicit val dateTimeCodec: CodecJson[DateTime] = CodecJson.derived[String].xmap(DateTime.parse)(_.toString())
  implicit val levelCodec: CodecJson[Level] = CodecJson.derived[String].xmap(Level(_))(_.name)
  implicit val uriCodec: CodecJson[URI] = CodecJson.derived[String].xmap(URI.create)(_.toString)
  implicit val mimeCodec: CodecJson[MIMEType] = CodecJson.derived[String].xmap(s => MIMEType(s).getOrElse(MIMEType
  .OctetStream))(_.toString)
  implicit val formatCodec: CodecJson[Format]  = CodecJson.derived[String].xmap(Format(_))(_.name)
  implicit val keywordCodec: CodecJson[Keyword]  = CodecJson.derived[String].xmap(Keyword)(_.name)
  implicit val tagCodec: CodecJson[ems.model.Tag]  = CodecJson.derived[String].xmap(ems.model.Tag)(_.name)
  implicit val localeCodec: CodecJson[Locale]  = CodecJson.derived[String].xmap(new Locale(_))(_.getLanguage)
  implicit val attachmentCodec: CodecJson[URIAttachment] = casecodec6(URIAttachment.apply, URIAttachment.unapply)(
    "id",
    "href",
    "name",
    "size",
    "media-type",
    "last-modified"
  )

  implicit val abstractCodec = casecodec11(Abstract.apply, Abstract.unapply)(
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
    "keywords"
  )
}

class SQLStorage(config: ems.SqlConfig, binaryStorage: BinaryStorage) extends DBStorage {
  val db = Database.forURL(config.url, config.username, config.password, driver = config.driver)
  import Codecs._

  implicit class FutureAwaiter[A](f: Future[A]) {
    def await(duration: scala.concurrent.duration.Duration = scala.concurrent.duration.Duration.Inf): A = Await.result(f, duration)
  }

  override def binary: BinaryStorage = binaryStorage

  override def shutdown(): Unit = db.close()

  override def getEvents() = {
    db.run(Tables.Events.to[Vector].result).map(_.map(toEvent)).await()
  }

  override def getEventsWithSessionCount(implicit user: User) = {
    val q = for {
      e <- Tables.Events
      s = sessionByEventAndUser(e.id, user).length
    } yield (e, s)

    db.run(q.to[Vector].result).map(_.map{
      case (row, length) => EventWithSessionCount(toEvent(row), length)
    }).await()
  }

  override def getEventsBySlug(name: String): Vector[Event] = {
    val q = for {
      e <- Tables.Events if e.slug === name
    } yield e

    db.run(q.to[Vector].result).map(_.map(toEvent)).await()
  }

  override def getEvent(id: UUID): Option[Event] = {
    val q = for {
      e <- Tables.Events if e.id === id
    } yield e

    db.run(q.result).map(_.headOption.map(toEvent)).await()
  }

  override def saveEvent(event: Event): Either[Throwable, Event] = ???

  override def getRooms(eventId: UUID): Vector[Room] = {
    val q = for {
      r <- Tables.Rooms if r.eventid === eventId
    } yield r

    db.run(q.to[Vector].result).map(_.map(toRoom)).await()
  }

  override def getRoom(eventId: UUID, id: UUID): Option[Room] = {
    val q = for {
      r <- Tables.Rooms if r.eventid === eventId && r.id === id
    } yield r

    db.run(q.result).map(_.headOption.map(toRoom)).await()
  }

  override def saveRoom(eventId: UUID, room: Room): Either[Throwable, Room] = ???

  override def removeRoom(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getSlots(eventId: UUID, parent: Option[UUID]): Vector[Slot] = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.parentid === parent
    } yield s

    db.run(q.to[Vector].result).map(_.map(toSlot)).await()
  }


  override def getSlot(eventId: UUID, id: UUID): Option[Slot] = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.id === id
    } yield s

    db.run(q.result).map(_.headOption.map(toSlot)).await()
  }


  override def saveSlot(slot: Slot): Either[Throwable, Slot] = ???

  override def removeSlot(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getSessions(eventId: UUID)(implicit user: User): Vector[ems.model.Session] = {
    val query = sessionByEventAndUser(eventId, user)
    db.run(query.to[Vector].result).map(_.map(toSession)).await()
  }

  override def getSessionsEnriched(eventId: UUID)(implicit user: User): Vector[EnrichedSession] = {
    val query = sessionByEventAndUser(eventId, user)
    db.run(query.to[Vector].result).map(_.map{ s =>
      EnrichedSession(toSession(s), s.roomid.flatMap(id => getRoom(eventId, id)), s.slotid.flatMap(id => getSlot(eventId, id)), getSpeakers(s.id), getAttachments(s.id))
    }).await()
  }

  override def getSessionEnriched(eventId: UUID, id: UUID)(implicit user: User): Option[EnrichedSession] = {
    val query = sessionByEventAndUser(eventId, user)
    db.run(query.result).map(_.headOption.map{ s =>
      EnrichedSession(toSession(s), s.roomid.flatMap(id => getRoom(eventId, id)), s.slotid.flatMap(id => getSlot(eventId, id)), getSpeakers(s.id), getAttachments(s.id))
    }).await()
  }

  override def getSessionsBySlug(eventId: UUID, slug: String)(implicit user: User): Vector[ems.model.Session] = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId && s.slug === slug
    } yield s

    val query = if (!user.authenticated) allSessions.filter(_.published === true) else allSessions

    db.run(query.to[Vector].result).map(_.map(toSession)).await()
  }

  override def getSession(eventId: UUID, id: UUID)(implicit user: User): Option[ems.model.Session] = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId && s.id === id
    } yield s

    val query = if (!user.authenticated) allSessions.filter(_.published === true) else allSessions
    db.run(query.result).map(_.headOption.map(toSession)).await()
  }

  override def getChangedSessions(eventId: UUID, from: DateTime)(implicit user: User): Vector[ems.model.Session] = { ???
    /*val query = sql"select id,eventId,slug,abstract,state,published,roomId,slotId,lastModified from session s where eventId = $eventId and lastModified > $from".query[Session].vector
    xa.trans(query).run*/
  }

  override def publishSessions(eventId: UUID, sessions: Seq[UUID]): Either[Throwable, Unit] = ???

  //override def saveSlotInSession(eventId: UUID, sessionId: UUID, slot: Slot): Either[Throwable, ems.model.Session] = ???

  //override def saveRoomInSession(eventId: UUID, sessionId: UUID, room: Room): Either[Throwable, ems.model.Session] = ???

  override def saveSession(session: ems.model.Session): Either[Throwable, ems.model.Session] = ???

  override def removeSession(sessionId: UUID): Either[Throwable, Unit] = ???

  override def saveAttachment(sessionId: UUID, attachment: URIAttachment): Either[Throwable, Unit] = ???

  override def status(): String = "OK"

  override def getAttachments(sessionId: UUID): Vector[URIAttachment] = {
    val q = for {
      a <- Tables.Session_Attachments if a.sessionid === sessionId
    } yield a

    db.run(q.to[Vector].result).map(_.map(toURIAttachment)).await()
  }

  override def getAttachment(sessionId: UUID, id: UUID): Option[URIAttachment] = {
    val q = for {
      a <- Tables.Session_Attachments if a.sessionid === sessionId
    } yield a

    db.run(q.result).map(_.headOption.map(toURIAttachment)).await()
  }

  override def removeAttachment(sessionId: UUID, id: UUID): Either[Throwable, Unit] = ???

  override def removeSpeaker(sessionId: UUID, speakerId: UUID): Either[Throwable, Unit] = ???

  override def saveSpeaker(sessionId: UUID,speaker: Speaker): Either[Throwable, Speaker] = ???

  override def getSpeakers(sessionId: UUID): Vector[Speaker] = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId
    } yield s

    db.run(q.to[Vector].result).map(_.map(toSpeaker)).await()
  }

  override def getSpeaker(sessionId: UUID, speakerId: UUID): Option[Speaker] = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId
    } yield s
    db.run(q.result).map(_.headOption.map(toSpeaker)).await()
  }

  override def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: Attachment with Entity[Attachment]): Either[Throwable, Unit] = ???

  private def sessionByEventAndUser(eventId: UUID, user: User): Query[Tables.Sessions, Tables.SessionRow, Seq] = {
    sessionByEventAndUser(eventId.asColumnOf[UUID], user)
  }

  private def sessionByEventAndUser(eventId: Rep[UUID], user: User): Query[Tables.Sessions, Tables.SessionRow, Seq] = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId
    } yield s

    if (!user.authenticated) allSessions.filter(_.published === true) else allSessions
  }


  private def toEvent(row: Tables.EventRow): Event = Event(Some(row.id), row.name, row.venue, row.slug, row.lastmodified)
  private def toRoom(row: Tables.RoomRow): Room = Room(Some(row.id), row.eventid, row.name, row.lastmodified)
  private def toSlot(row: Tables.SlotRow): Slot = Slot(Some(row.id), row.eventid, row.start, Minutes.minutes(row.duration).toStandardDuration, row.parentid, row.lastmodified)
  private def toSpeaker(row: Tables.SpeakerRow): Speaker = {
    val props = row.attributes.jdecode[SpeakerProperties].fold((e, _) => sys.error(e), identity)
    val photo = row.photo.flatMap(UUIDFromStringOpt).flatMap(p => binaryStorage.getAttachment(p))
    Speaker(Some(row.id), props.name, row.email, props.zipCode, props.bio, Set.empty, photo, row.lastmodified)
  }
  private def toSession(row: Tables.SessionRow): ems.model.Session = {
    val decodedabs = row.abs.jdecode[Abstract].fold((e, history) => sys.error("Fail decoding: " + (e, history)), identity)
    Session(Some(row.id), row.eventid, row.slug, row.roomid, row.slotid, decodedabs, State(row.state), row.published, row.lastmodified)
  }

  private def toURIAttachment(row: Tables.SessionAttachmentRow): URIAttachment = {
    URIAttachment(Some(row.id), URI.create(row.href), row.name, row.size, row.mimetype.flatMap(MIMEType.apply), row.lastmodified)
  }

  case class SpeakerProperties(name: String, bio: Option[String], zipCode: Option[String])

  object SpeakerProperties {
    implicit val tagcodec = CodecJson.derived[String].xmap(ems.model.Tag.apply)(_.name)
    implicit val codec: CodecJson[SpeakerProperties] =
      casecodec3(SpeakerProperties.apply, SpeakerProperties.unapply)("name", "bio", "zipcode")
  }
}
