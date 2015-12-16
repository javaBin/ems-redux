package ems
package storage

import java.net.URI

import ems.security.User
import model._

import scala.concurrent.Future

trait DBStorage {
  def binary: BinaryStorage

  def getEvents(): Future[Vector[Event]]

  def getEventsWithSessionCount(implicit user: User): Future[Vector[EventWithSessionCount]]

  def getEvent(id: UUID): Future[Option[Event]]

  def getEventBySlug(name: String): Future[Option[UUID]]

  def saveEvent(event: Event): Future[Event]

  def getSlots(eventId: UUID, parent: Option[UUID] = None): Future[Vector[Slot]]

  def getSlot(eventId: UUID, id: UUID): Future[Option[Slot]]

  def saveSlot(slot: Slot): Future[Slot]

  def removeSlot(eventId: UUID, id: UUID): Future[Unit]

  def getRooms(eventId: UUID): Future[Vector[Room]]

  def getRoom(eventId: UUID, id: UUID): Future[Option[Room]]

  def saveRoom(eventId: UUID, room: Room): Future[Room]

  def removeRoom(eventId: UUID, id: UUID): Future[Unit]

  def getSessions(eventId: UUID)(implicit user: User): Future[Vector[Session]]

  def getSessionsEnriched(eventId: UUID)(implicit user: User): Future[Vector[EnrichedSession]]

  def getSessionEnriched(eventId: UUID, id: UUID)(implicit user: User): Future[Option[EnrichedSession]]

  def getSessionBySlug(eventId: UUID, slug: String)(implicit user: User): Future[Option[UUID]]

  def getSession(eventId: UUID, id: UUID)(implicit user: User): Future[Option[Session]]

  def saveSession(session: Session): Future[Session]

  def publishSessions(eventId: UUID, sessions: Seq[UUID]): Future[Unit]

  def getSpeakers(sessionId: UUID): Future[Vector[Speaker]]

  def getSpeaker(sessionId: UUID, speakerId: UUID): Future[Option[Speaker]]

  def saveSpeaker(sessionId: UUID, speaker: Speaker): Future[Speaker]

  def removeSession(sessionId: UUID): Future[Unit]

  def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: URI):  Future[Unit]

  def removeSpeaker(sessionId: UUID, speakerId: UUID): Future[Unit]

  def status(): String

  def shutdown()
}
