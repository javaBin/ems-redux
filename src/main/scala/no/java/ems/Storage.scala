package no.java.ems


/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 11/1/11
 * Time: 10:26 PM
 * To change this template use File | Settings | File Templates.
 */

trait Storage {
  def getEvents(): List[Event]

  def getEventsByName(title: String): List[Event]

  def getEvent(id: String): Option[Event]

  def saveEvent(event: Event) : Event

  def getSessions(eventId: String): List[Session]

  def getSessionsByTitle(eventId: String, title: String): List[Session]

  def getSession(eventId: String, id: String): Option[Session]

  def saveSession(session: Session) : Session

  def getContact(id: String): Option[Contact]

  def getContacts(): List[Contact]

  def saveContact(contact: Contact) : Contact

  def getAttachment(id: String): Option[Attachment with Entity]

  def saveAttachment(att: Attachment): Attachment with Entity

  def removeAttachment(id: String)

  def saveEntity[T <: Entity](entity: T) = entity match {
    case e: Event => saveEvent(e)
    case s: Session => saveSession(s)
    case c: Contact => saveContact(c)
    case _ => throw new IllegalArgumentException("Usupported entity: " + entity)
  }

  def shutdown()
}