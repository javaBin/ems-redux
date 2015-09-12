package ems
package storage

import ems.security.User
import model._
import org.joda.time.DateTime

trait DBStorage {
  def binary: BinaryStorage

  def getEvents(): Vector[Event]

  def getEventsWithSessionCount(implicit user: User): Vector[EventWithSessionCount]

  def getEvent(id: UUID): Option[Event]

  def getEventsBySlug(name: String): Vector[Event]

  def saveEvent(event: Event): Either[Throwable, Event]

  def getSlots(eventId: UUID, parent: Option[UUID] = None): Vector[Slot]

  def getSlot(eventId: UUID, id: UUID): Option[Slot]

  def saveSlot(slot: Slot): Either[Throwable, Slot]

  def removeSlot(eventId: UUID, id: UUID): Either[Throwable, String]

  def getRooms(eventId: UUID): Vector[Room]

  def getRoom(eventId: UUID, id: UUID): Option[Room]

  def saveRoom(eventId: UUID, room: Room): Either[Throwable, Room]

  def removeRoom(eventId: UUID, id: UUID): Either[Throwable, String]

  def getSessions(eventId: UUID)(implicit user: User) : Vector[Session]

  def getSessionsBySlug(eventId: UUID, slug: String)(implicit user: User): Vector[Session]

  def getSession(eventId: UUID, id: UUID)(implicit user: User): Option[Session]

  def saveSession(session: Session): Either[Throwable, Session]

  def publishSessions(eventId: UUID, sessions: Seq[UUID]): Either[Throwable, Unit]

  def saveSlotInSession(eventId: UUID, sessionId: UUID, slot: Slot): Either[Throwable, Session]

  def saveRoomInSession(eventId: UUID, sessionId: UUID, room: Room): Either[Throwable, Session]

  def saveAttachment(eventId: UUID, sessionId: UUID, attachment: URIAttachment): Either[Throwable, Unit]

  def removeAttachment(eventId: UUID, sessionId: UUID, id: UUID): Either[Throwable, Unit]

  def getSpeakers(sessionId: UUID): Vector[Speaker]

  def getSpeaker(sessionId: UUID, speakerId: UUID): Option[Speaker]

  def saveSpeaker(sessionId: UUID, speaker: Speaker): Either[Throwable, Speaker]

  def removeSession(sessionId: UUID): Either[Throwable, Unit]

  def updateSpeakerWithPhoto(sessionId: UUID, speakerId: UUID, photo: Attachment with Entity[Attachment]):  Either[Throwable, Unit]

  def removeSpeaker(sessionId: UUID, speakerId: UUID): Either[Throwable, Unit]

  def getChangedSessions(eventId: UUID, from: DateTime)(implicit user: User): Vector[Session]

  def status(): String

  def shutdown()
}
