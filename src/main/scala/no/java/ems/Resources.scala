package no.java.ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan
import java.net.URI
import java.io.{ByteArrayOutputStream, InputStream}
import java.util.concurrent.TimeUnit
import java.util.{Locale, Date}
import org.joda.time.{DateTime, Minutes}

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
      var read = 0;
      val buffer = new Array[Byte](1024 * 4)
      while (read != -1) {
        read = inputStream.read(buffer)
        out.write(buffer, 0, read)
      }
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

class Resources(storage: Storage) extends Plan {

  def handleEventsList() = {
    val output = storage.getEvents().map(_.toString).mkString(",")
    ContentType("text/plain") ~> ResponseString(output)
  }

  def handleEvent(id: String, request: HttpRequest[Any]) = {
    request match {
      case GET(_) => storage.getEvent(id).map(x => ContentType("text/plain") ~> ResponseString("Hello to event " + x)).getOrElse(NotFound)
      case _ => NotImplemented
    }
  }

  def handleSessions(eventId : String) = {
    ContentType("text/plain") ~> ResponseString("Hello to sessions for event " + eventId)
  }

  def handleSession(eventId: String, sessionId: String, request: HttpRequest[Any]) = {
    request match {
      case GET(_) => storage.getSession(eventId, sessionId).map(x => ContentType("text/plain") ~> ResponseString("Hello to session %s in event %s \n".format(sessionId, eventId))).getOrElse(NotFound ~> ResponseString("Session was not found"))
      case _ => NotImplemented
     }
  }

  def intent = {
    case GET(Path(Seg("events" :: Nil))) => handleEventsList();
    case req @ Path(Seg("events" :: id :: Nil)) => handleEvent(id, req);
    case GET(Path(Seg("events" :: eventId :: "sessions" :: Nil))) => handleSessions(eventId);
    case req @ Path(Seg("events" :: eventId :: "sessions" :: id :: Nil)) => handleSession(eventId, id, req);
  }
}


object Main extends App {
  val storage = new MemoryStorage()
  val resources = new Resources(storage)

  def populate(storage: Storage) {
    val event = storage.saveEvent(Event(None, "JavaZone 2011", new DateTime(), new DateTime()))
    val sessions = List(Session(event.id.get, "Session 1"), Session(event.id.get, "Session 2"))
    for (s <- sessions) storage.saveSession(s)
  }
  
  populate(storage)

  unfiltered.jetty.Http(8080).plan(resources).run()
}