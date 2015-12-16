package ems
package storage

import java.net.URI
import java.util.{UUID, Locale}

import ems.model._
import ems.security.User
import org.joda.time.{Minutes, DateTime}
import scala.concurrent.Future
import argonaut._, Argonaut._
import slick.driver.PostgresDriver.api._
import scala.concurrent.ExecutionContext.Implicits.global

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

  override def binary: BinaryStorage = binaryStorage

  override def shutdown(): Unit = db.close()

  override def getEvents() = {
    db.run(Tables.Events.to[Vector].result).map(_.map(toEvent))
  }

  override def getEventsWithSessionCount(implicit user: User) = {
    val q = for {
      e <- Tables.Events
      s = sessionByEventAndUser(e.id, user).length
    } yield (e, s)

    db.run(q.to[Vector].result).map(_.map{
      case (row, length) => EventWithSessionCount(toEvent(row), length)
    })
  }

  override def getEventBySlug(name: String) = {
    val q = for {
      e <- Tables.Events if e.slug === name
    } yield e.id

    db.run(q.result.headOption)
  }

  override def getEvent(id: UUID) = {
    val q = for {
      e <- Tables.Events if e.id === id
    } yield e

    db.run(q.result.headOption).map(_.map(toEvent))
  }

  override def saveEvent(event: Event) = {
    val id = event.id.getOrElse(randomUUID)
    val data = Tables.EventRow(id, event.name, event.venue, event.slug, DateTime.now())
    val action = if (event.id.isDefined) {
      val query = for { s <- Tables.Events if s.id === id } yield s
      query.update(data)
    } else {
      Tables.Events += data
    }

    db.run(action).map(_ => event.withId(id).copy(lastModified = data.lastmodified))
  }

  override def getRooms(eventId: UUID) = {
    val q = for {
      r <- Tables.Rooms if r.eventid === eventId
    } yield r

    db.run(q.to[Vector].result).map(_.map(toRoom))
  }

  override def getRoom(eventId: UUID, id: UUID) = {
    val q = for {
      r <- Tables.Rooms if r.eventid === eventId && r.id === id
    } yield r

    db.run(q.result.headOption).map(_.map(toRoom))
  }

  override def saveRoom(eventId: UUID, room: Room) = {
    val id = room.id.getOrElse(randomUUID)
    val data = Tables.RoomRow(id, eventId, room.name, DateTime.now())
    val action = if (room.id.isDefined) {
      val query = for {
        r <- Tables.Rooms if r.eventid === eventId && r.id === id
      } yield r
      query.update(data)
    } else {
      Tables.Rooms += data
    }

    db.run(action).map(_ => room.withId(id).copy(lastModified = data.lastmodified))
  }

  override def removeRoom(eventId: UUID, id: UUID) = {
    val query = for {
      r <- Tables.Rooms if r.eventid === eventId && r.id === id
    } yield r

    db.run(query.delete).map(_ => ())
  }

  override def getSlots(eventId: UUID, parent: Option[UUID]) = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.parentid === parent
    } yield s

    db.run(q.to[Vector].result).map(_.map(toSlot))
  }

  override def getSlot(eventId: UUID, id: UUID) = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.id === id
    } yield s

    db.run(q.result.headOption).map(_.map(toSlot))
  }

  override def saveSlot(slot: Slot) = {
    val id = slot.id.getOrElse(randomUUID)
    val data = Tables.SlotRow(id, slot.eventId, slot.parentId, slot.start, slot.duration.getStandardMinutes.toInt, DateTime.now())
    val action = if (slot.id.isDefined) {
      val q = for {
        s <- Tables.Slots if s.eventid === slot.eventId && s.id === id
      } yield s
      q.update(data)
    } else {
      Tables.Slots += data
    }
    db.run(action).map(_ => slot.withId(id).copy(lastModified = data.lastmodified))
  }

  override def removeSlot(eventId: UUID, id: UUID) = {
    val q = for {
      s <- Tables.Slots if s.eventid === eventId && s.id === id
    } yield s
    db.run(q.delete).map(_ => ())
  }

  override def getSessions(eventId: UUID)(implicit user: User) = {
    val query = sessionByEventAndUser(eventId, user)
    db.run(query.to[Vector].result).map(_.map(toSession))
  }

  override def getSessionsEnriched(eventId: UUID)(implicit user: User) = {
    val query = sessionByEventAndUser(eventId, user)
    db.run(query.to[Vector].result).flatMap(sessions => Future.sequence(sessions.map{ s =>
      enrich(eventId, s.id, s.roomid, s.roomid).map{
        case (room, slot, speakers) => {
          EnrichedSession(toSession(s), room, slot, speakers)
        }
      }
    }))
  }

  override def getSessionEnriched(eventId: UUID, id: UUID)(implicit user: User) = {
    val query = for {
      s <- sessionByEventAndUser(eventId, user) if s.id === id
    } yield s

    for {
      res <- db.run(query.result.headOption)
      opt <- res.map( s =>
        enrich(eventId, id, s.roomid, s.roomid).map{
          case (room, slot, speakers) => {
            Some(EnrichedSession(toSession(s), room, slot, speakers))
          }
          case _ => None
        }
      ).getOrElse(Future.successful(None))
    } yield opt
  }



  override def getSessionBySlug(eventId: UUID, slug: String)(implicit user: User) = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId && s.slug === slug
    } yield (s.id, s.published)

    val query = if (!user.authenticated) allSessions.filter(_._2 === true) else allSessions

    db.run(query.result.headOption).map(_.map(_._1))
  }

  override def getSession(eventId: UUID, id: UUID)(implicit user: User) = {
    val allSessions = for {
      s <- Tables.Sessions if s.eventid === eventId && s.id === id
    } yield s

    val query = if (!user.authenticated) allSessions.filter(_.published === true) else allSessions
    db.run(query.result.headOption).map(_.map(toSession))
  }

  override def publishSessions(eventId: UUID, sessions: Seq[UUID]) = {
    def updatePublished(id: UUID) =
      (for { s <- Tables.Sessions if s.eventid === eventId && s.id === id } yield s.published).update(true)

    db.run(DBIO.seq(sessions.map(updatePublished) : _*))
  }

  override def saveSession(session: ems.model.Session) = {
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

    db.run(action).map(_ => session.withId(id))
  }

  override def removeSession(sessionId: UUID) = {
    val query = for { s <- Tables.Sessions if s.id === sessionId } yield s
    db.run(query.delete).map(_ => ())
  }

  override def status(): String = "OK"

  override def removeSpeaker(sessionId: UUID, speakerId: UUID) = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId && s.id === speakerId
    } yield s
    db.run(q.delete).map(_ => ())
  }

  override def saveSpeaker(sessionId: UUID, speaker: Speaker) = {
    val id = speaker.id.getOrElse(randomUUID)
    val attributes = SpeakerProperties(speaker.name, speaker.bio, speaker.zipCode, speaker.labels)
    val data = Tables.SpeakerRow(
      id,
      sessionId,
      speaker.email,
      attributes.asJson,
      speaker.photo.flatMap(_.id.map(_.toString)),
      DateTime.now()
    )

    val action = if (speaker.id.isDefined) {
      val q = for {
        s <- Tables.Speakers if s.sessionid === sessionId && s.id === id
      } yield s
      q.update(data)
    }
    else {
      Tables.Speakers += data
    }

    db.run(action).map(_ => speaker.withId(id).copy(lastModified = data.lastmodified))
  }

  override def getSpeakers(sessionId: UUID) = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId
    } yield s

    db.run(q.to[Vector].result).map(_.map(toSpeaker))
  }

  override def getSpeaker(sessionId: UUID, speakerId: UUID) = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId && s.id === speakerId
    } yield s
    db.run(q.result.headOption).map(_.map(toSpeaker))
  }

  override def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: URI) = {
    val q = for {
      s <- Tables.Speakers if s.sessionid === sessionId && s.id === speakerId
    } yield s.photo
    db.run(q.update(Some(photo.toString))).map(_ => ())
  }

  private def enrich(eventId: UUID, sessionId: UUID, roomId: Option[UUID], slotId: Option[UUID]) = {
    for {
      r <- roomId.map(id => getRoom(eventId, id)).getOrElse(Future.successful(None))
      s <- slotId.map(id => getSlot(eventId, id)).getOrElse(Future.successful(None))
      speakers <- getSpeakers(sessionId)
    } yield (r, s, speakers)
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
