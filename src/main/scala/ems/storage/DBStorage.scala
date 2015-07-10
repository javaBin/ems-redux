package ems
package storage

import ems.security.User
import model._
import org.joda.time.DateTime

trait DBStorage {
  def binary: BinaryStorage

  def getEvents(): Vector[Event]

  def getEventsWithSessionCount(user: User): Vector[EventWithSessionCount]

  def getEvent(id: String): Option[Event]

  def getEventsBySlug(name: String): Vector[Event]

  def saveEvent(event: Event): Either[Throwable, Event]

  def getSlots(eventId: String, parent: Option[String] = None): Vector[Slot]

  def getAllSlots(eventId: String): Vector[SlotTree]

  def getSlot(id: String): Option[Slot]

  def saveSlot(slot: Slot): Either[Throwable, Slot]

  def removeSlot(eventId: String, id: String): Either[Throwable, String]

  def getRooms(eventId: String): Vector[Room]

  def getRoom(eventId: String, id: String): Option[Room]

  def saveRoom(eventId: String, room: Room): Either[Throwable, Room]

  def removeRoom(eventId: String, id: String): Either[Throwable, String]

  def getSessions(eventId: String)(user: User) : Vector[Session]

  def getSessionCount(eventId: String)(user: User): Int

  def getSessionsBySlug(eventId: String, slug: String): Vector[Session]

  def getSession(eventId: String, id: String): Option[Session]

  def saveSession(session: Session): Either[Throwable, Session]

  def publishSessions(eventId: String, sessions: Seq[String]): Either[Throwable, Unit]

  def saveSlotInSession(eventId: String, sessionId: String, slot: Slot): Either[Throwable, Session]

  def saveRoomInSession(eventId: String, sessionId: String, room: Room): Either[Throwable, Session]

  def saveAttachment(eventId: String, sessionId: String, attachment: URIAttachment): Either[Throwable, Unit]

  def removeAttachment(eventId: String, sessionId: String, id: String): Either[Throwable, Unit]

  def getSpeakers(eventId: String, sessionId: String): Vector[Speaker]

  def getSpeaker(eventId: String, sessionId: String, speakerId: String): Option[Speaker]

  def saveSpeaker(eventId: String, sessionId: String, speaker: Speaker): Either[Throwable, Speaker]

  def removeSession(sessionId: String): Either[Throwable, Unit]

  def updateSpeakerWithPhoto(eventId: String, sessionId: String, speakerId: String, photo: Attachment with Entity[Attachment]):  Either[Throwable, Unit]

  def removeSpeaker(eventId: String, sessionId: String, speakerId: String): Either[Throwable, Unit]

  def getChangedSessions(from: DateTime)(implicit u: User): Vector[Session]

  def status(): String

  def shutdown()
}
