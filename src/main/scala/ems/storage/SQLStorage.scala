package ems
package storage

import java.net.URI
import java.util.{UUID, Locale}

import ems.model._
import ems.security.User
import org.joda.time.{Minutes, DateTime}
import scala.concurrent.{Future, Await}
import argonaut._, Argonaut._
import slick.driver.PostgresDriver.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.Exception.allCatch

object Codecs {
  implicit val uuidCodec: CodecJson[UUID] = CodecJson.derived[String].xmap(UUIDFromString)(UUIDToString)
  implicit val dateTimeCodec: CodecJson[DateTime] = CodecJson.derived[String].xmap(DateTime.parse)(_.toString())
  implicit val levelCodec: CodecJson[Level] = CodecJson.derived[String].xmap(Level(_))(_.name)
  implicit val formatCodec: CodecJson[Format]  = CodecJson.derived[String].xmap(Format(_))(_.name)
  implicit val localeCodec: CodecJson[Locale]  = CodecJson.derived[String].xmap(new Locale(_))(_.getLanguage)

  implicit val abstractCodec = casecodec10(Abstract.apply, Abstract.unapply)(
    "title",
    "summary",
    "body",
    "audience",
    "outline",
    "equipment",
    "language",
    "level",
    "format",
    "labels"
  )
}

class SQLStorage(config: ems.SqlConfig, binaryStorage: BinaryStorage) extends DBStorage {
  val db = Database.forDataSource(config.dataSource())
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

  override def getEventBySlug(name: String): Option[UUID] = {
    val q = for {
      e <- Tables.Events if e.slug === name
    } yield e.id

    db.run(q.result.headOption).await()
  }

  override def getEvent(id: UUID): Option[Event] = {
    val q = for {
      e <- Tables.Events if e.id === id
    } yield e

    db.run(q.result.headOption).map(_.map(toEvent)).await()
  }

  override def saveEvent(event: Event): Either[Throwable, Event] = ???

  override def getRooms(eventId: UUID): Vector[Room] = {
    val q = for {
      r <- Tables.Rooms if r.eventid === eventId
    } yield r

    db.run(q.to[Vector].result).map(_.map(toRoom)).await()
  }

  override def getRoom(eventId: UUID, id: UUID): Option[Room] = getRoomF(eventId, id).await()

  def getRoomF(eventId: UUID, id: UUID): Future[Option[Room]] = {
    val q = for {
      r <- Tables.Rooms if r.eventid === eventId && r.id === id
    } yield r

    db.run(q.result.headOption).map(_.map(toRoom))
  }

  override def saveRoom(eventId: UUID, room: Room): Either[Throwable, Room] = ???

  override def removeRoom(eventId: UUID, id: UUID): Either[Throwable, String] = ???

  override def getSlots(eventId: UUID, parent: Option[UUID]): Vector[Slot] = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.parentid === parent
    } yield s

    db.run(q.to[Vector].result).map(_.map(toSlot)).await()
  }

  override def getSlot(eventId: UUID, id: UUID): Option[Slot] = getSlotF(eventId, id).await()

  def getSlotF(eventId: UUID, id: UUID): Future[Option[Slot]] = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.id === id
    } yield s

    db.run(q.result.headOption).map(_.map(toSlot))
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
      EnrichedSession(toSession(s), s.roomid.flatMap(id => getRoom(eventId, id)), s.slotid.flatMap(id => getSlot(eventId, id)), getSpeakers(s.id))
    }).await()
  }

  override def getSessionEnriched(eventId: UUID, id: UUID)(implicit user: User): Option[EnrichedSession] = {
    val query = sessionByEventAndUser(eventId, user)
    db.run(query.result.headOption).map(_.map{ s =>
      EnrichedSession(toSession(s), s.roomid.flatMap(id => getRoom(eventId, id)), s.slotid.flatMap(id => getSlot(eventId, id)), getSpeakers(s.id))
    }).await()
  }

  override def getSessionBySlug(eventId: UUID, slug: String)(implicit user: User): Option[UUID] = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId && s.slug === slug
    } yield (s.id, s.published)

    val query = if (!user.authenticated) allSessions.filter(_._2 === true) else allSessions

    db.run(query.result.headOption).map(_.map(_._1)).await()
  }

  override def getSession(eventId: UUID, id: UUID)(implicit user: User): Option[ems.model.Session] = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId && s.id === id
    } yield s

    val query = if (!user.authenticated) allSessions.filter(_.published === true) else allSessions
    db.run(query.result.headOption).map(_.map(toSession)).await()
  }

  override def publishSessions(eventId: UUID, sessions: Seq[UUID]): Either[Throwable, Unit] = {
    def updatePublished(id: UUID) =
      (for { s <- Tables.Sessions if s.eventid === eventId && s.id === id } yield s.published).update(true)

    allCatch.either(db.run(DBIO.seq(sessions.map(updatePublished) : _*)).await())
  }

  override def saveSession(session: ems.model.Session): Either[Throwable, ems.model.Session] = {
    val id: UUID = session.id.getOrElse(UUID.randomUUID())
    val row = Tables.SessionRow(
      id = id,
      eventid = session.eventId,
      slug = session.slug,
      abs = session.abs.asJson,
      state = session.state.name,
      published = session.published,
      roomid = session.room,
      slotid = session.slot,
      lastmodified = DateTime.now()
    )
    val action = if (session.id.isDefined) {
      val query = for { s <- Tables.Sessions if s.id === session.id.get } yield s
      query.update(row)
    } else {
      Tables.Sessions += row
    }

    allCatch.either(db.run(action).map(_ => session.withId(id)).await())
  }

  override def removeSession(sessionId: UUID): Either[Throwable, Unit] = {
    val query = for { s <- Tables.Sessions if s.id === sessionId } yield s
    allCatch.either(db.run(query.delete).await())
  }

  override def status(): String = "OK"

  override def removeSpeaker(sessionId: UUID, speakerId: UUID): Either[Throwable, Unit] = {
    ???
  }

  override def saveSpeaker(sessionId: UUID,speaker: Speaker): Either[Throwable, Speaker] = {
    ???
  }

  override def getSpeakers(sessionId: UUID): Vector[Speaker] = getSpeakersF(sessionId).await()

  def getSpeakersF(sessionId: UUID): Future[Vector[Speaker]] = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId
    } yield s

    db.run(q.to[Vector].result).map(_.map(toSpeaker))
  }

  override def getSpeaker(sessionId: UUID, speakerId: UUID): Option[Speaker] = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId && s.id === speakerId
    } yield s
    db.run(q.result.headOption).map(_.map(toSpeaker)).await()
  }

  override def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: URI): Either[Throwable, Unit] = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId && s.id === speakerId
    } yield s.photo
    allCatch.either(db.run(q.update(Some(photo.toString))).await())
  }

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
    val props = row.attributes.jdecode[SpeakerProperties].fold((e, history) => sys.error("Fail decoding: " + (e, history)), identity)
    val photo = row.photo.flatMap(UUIDFromStringOpt).flatMap(p => binaryStorage.getAttachment(p))
    Speaker(Some(row.id), props.name, row.email, props.zipCode, props.bio, props.labels, photo, row.lastmodified)
  }
  private def toSession(row: Tables.SessionRow): ems.model.Session = {
    val decodedabs = row.abs.jdecode[Abstract].fold((e, history) => sys.error("Fail decoding: " + (e, history)), identity)
    Session(Some(row.id), row.eventid, row.slug, row.roomid, row.slotid, decodedabs, row.video.map(URI.create), State(row.state), row.published, row.lastmodified)
  }

  case class SpeakerProperties(name: String, bio: Option[String], zipCode: Option[String], labels: Map[String, List[String]] = Map.empty)

  object SpeakerProperties {
    implicit val codec: CodecJson[SpeakerProperties] = CodecJson(
      props => Json.obj(
         "name"     :=  props.name,
         "bio"      :=  props.bio,
         "zipcode"  :=  props.zipCode,
         "labels"   :=  Json.obj(
            props.labels.map{case (k,v) => k := v}.toList : _*
         )
      ),
      c => for {
        name    <- (c --\ "name").as[String]
        bio     <- (c --\ "bio").as[Option[String]]
        zipcode <- (c --\ "zipcode").as[Option[String]]
        labels  <- (c --\ "labels").as[Map[String, List[String]]] ||| DecodeResult.ok(Map.empty[String, List[String]])
      } yield SpeakerProperties(name, bio, zipcode, labels)
    )
  }
}
