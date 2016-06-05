package ems.graphql

import java.net.URI
import java.util.UUID

import ems.model.{EnrichedSession, Event, EventWithSessionCount, Room, Session, Slot, Speaker}
import ems.security.User
import ems.storage.{BinaryStorage, DBStorage}

import scala.concurrent.{ExecutionContext, Future}

class DummyDbStorage(implicit ec: ExecutionContext) extends DBStorage {
  override def binary: BinaryStorage = ???

  override def publishSessions(eventId: UUID, sessions: Seq[UUID]): Future[Unit] = ???

  override def getRooms(eventId: UUID): Future[Vector[Room]] = ???

  override def saveSession(session: Session): Future[Session] = ???

  override def getSessionBySlug(eventId: UUID, slug: String)(implicit user: User): Future[Option[UUID]] = ???

  override def saveSpeaker(sessionId: UUID, speaker: Speaker): Future[Speaker] = ???

  override def getSessions(eventId: UUID)(implicit user: User): Future[Vector[Session]] = ???

  override def shutdown(): Unit = ???

  override def getRoom(eventId: UUID, id: UUID): Future[Option[Room]] = ???

  override def removeSlot(eventId: UUID, id: UUID): Future[Unit] = ???

  override def getSlot(eventId: UUID, id: UUID): Future[Option[Slot]] = ???

  override def getSlots(eventId: UUID, parent: Option[UUID]): Future[Vector[Slot]] = ???

  override def getSessionEnriched(eventId: UUID, id: UUID)(implicit user: User): Future[Option[EnrichedSession]] = ???

  override def getSpeakers(sessionId: UUID): Future[Vector[Speaker]] = ???

  override def getSessionsEnriched(eventId: UUID)(implicit user: User): Future[Vector[EnrichedSession]] = ???

  override def getEvents(): Future[Vector[Event]] = Future(Vector(
    Event(
      id = Some(UUID.fromString("d7af21bd-e040-4e1f-9b45-71918b5e46cd")),
      name = "Event 1",
      slug = "slug event 1",
      venue = "venue for event 1"
    ),
    Event(
      id = Some(UUID.fromString("c560446e-a2e8-43ad-80de-c16d2589eeda")),
      name = "Event 2",
      slug = "slug event 2",
      venue = "venue for event 2"
    )
  ))

  override def saveRoom(eventId: UUID, room: Room): Future[Room] = ???

  override def getEvent(id: UUID): Future[Option[Event]] = {
    getEvents().map(_.find(_.id.get == id))
  }

  override def removeSession(sessionId: UUID): Future[Unit] = ???

  override def status(): Future[String] = ???

  override def getEventsWithSessionCount(implicit user: User): Future[Vector[EventWithSessionCount]] = ???

  override def removeSpeaker(sessionId: UUID, speakerId: UUID): Future[Unit] = ???

  override def getSession(eventId: UUID, id: UUID)(implicit user: User): Future[Option[Session]] = ???

  override def saveSlot(slot: Slot): Future[Slot] = ???

  override def getSpeaker(sessionId: UUID, speakerId: UUID): Future[Option[Speaker]] = ???

  override def saveEvent(event: Event): Future[Event] = ???

  override def getEventBySlug(name: String): Future[Option[UUID]] = ???

  override def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: URI): Future[Unit] = ???

  override def removeRoom(eventId: UUID, id: UUID): Future[Unit] = ???
}
