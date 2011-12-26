package no.java.ems

import java.net.URI
import java.io.{ByteArrayOutputStream, InputStream}

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 11/1/11
 * Time: 10:26 PM
 * To change this template use File | Settings | File Templates.
 */

trait Storage {
  def getEvents(): List[Event]

  def getEvent(id: String): Option[Event]

  def saveEvent(event: Event) : Event

  def getSessions(eventId: String): List[Session]

  def getSession(eventId: String, id: String): Option[Session]

  def saveSession(session: Session) : Session

  def getContact(id: String): Option[Contact]

  def getContacts(): List[Contact]

  def saveContact(contact: Contact) : Contact

  def getAttachment(id: String): Option[ByteArrayAttachment]

  def saveAttachment(att: Attachment): ByteArrayAttachment
}

class MemoryStorage extends Storage {
  import collection.mutable.HashMap
  import java.util.UUID

  private val events = HashMap[String, Event]()
  private val sessions = HashMap[String, Session]()
  private val contacts = HashMap[String, Contact]()
  private val attachments = HashMap[String, ByteArrayAttachment]()
 
  def getEvents() = events.values.toList

  def getEvent(id: String) = events.get(id)

  def getSessions(eventId: String) = sessions.values.filter(_.eventId == eventId).toList

  def getSession(eventId: String, id: String) = sessions.get(eventId).filter(_.eventId == eventId)

  def getContact(id: String) = contacts.get(id)

  def getContacts() = contacts.values.toList
  
  def getAttachment(id: String) = attachments.get(id)

  def saveEvent(event: Event) = {
    val id = event.id.getOrElse(UUID.randomUUID().toString)
    val saved = event.copy(Some(id))
    events.put(id, saved)
    saved
  }

  def saveSession(session: Session) = {
    val id = session.id.getOrElse(UUID.randomUUID().toString)
    val saved = session.copy(Some(id))
    sessions.put(id, saved)
    saved
  }

  def saveContact(contact: Contact) = {
    val id = contact.id.getOrElse(UUID.randomUUID().toString)
    val saved = contact.copy(Some(id))
    contacts.put(id, saved)
    saved
  }
  
  def saveAttachment(att: Attachment) = {
    val bytes = att match {
      case a: ByteArrayAttachment => a
      case a => a.toByteArrayAttachment(Some(UUID.randomUUID().toString))
    }
    attachments.put(bytes.id.get, bytes)
    bytes
  }
}

object MemoryStorage {
  private lazy val cache = new MemoryStorage()
  def apply() : MemoryStorage = cache
}