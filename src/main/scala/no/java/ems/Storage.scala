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

  def getAttachment(uri: URI): Option[Attachment]

  def getEvent(id: String): Option[Event]

  def saveEvent(event: Event) : Event

  def getSessions(eventId: String): List[Session]

  def getSession(eventId: String, id: String): Option[Session]

  def saveSession(session: Session) : Session

  def saveAttachment(uri: URI, attachment: Attachment)

  def getContact(id: String): Option[Contact]

  def getContacts(): List[Contact]

  def saveContact(contact: Contact) : Contact
}

class MemoryStorage extends Storage {
  import collection.mutable.HashMap
  import java.util.UUID

  private val events = HashMap[String, Event]()
  private val sessions = HashMap[String, Session]()
  private val contacts = HashMap[String, Contact]()
  private val attachments = HashMap[URI, Attachment]()

  def getEvents() = events.values.toList

  def getEvent(id: String) = events.get(id)

  def getSessions(eventId: String) = sessions.values.filter(_.eventId == eventId).toList

  def getSession(eventId: String, id: String) = sessions.get(eventId).filter(_.eventId == eventId)

  def getContact(id: String) = contacts.get(id)

  def getContacts() = contacts.values.toList

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

  def getAttachment(uri: URI) = attachments.get(uri)

  def toBytes(inputStream: InputStream): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    try {
      var read = 0
      val buffer = new Array[Byte](1024 * 4)
      do {
        read = inputStream.read(buffer)
        out.write(buffer, 0, read)
      } while (read != -1)
    }
    finally {
      inputStream.close()
    }
    out.toByteArray
  }

  def saveAttachment(uri: URI, attachment: Attachment) = {
    attachment match {
      case a @ URIAttachment(name, size, mt, _) => attachments.put(uri, new ByteArrayAttachment(name, size, mt, toBytes(a.data)))
      case a : ByteArrayAttachment => attachments.put(uri, a)
    }
  }
}

object MemoryStorage {
  private lazy val cache = new MemoryStorage()
  def apply() : MemoryStorage = cache
}