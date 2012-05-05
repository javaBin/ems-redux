package no.java.ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan
import org.joda.time.DateTime
import javax.servlet.http.HttpServletRequest
import no.java.unfiltered._

trait Resources extends Plan with EventResources with ContactResources with AttachmentHandler { this: Storage =>

  def intent = {
    case req@Path(Seg(Nil)) => handleRoot(req)
    case req@Path(Seg("contacts" :: Nil)) => handleContactList(req)
    case req@Path(Seg("contacts" :: id :: Nil)) => handleContact(id, req)
    case req@Path(Seg("contacts" :: id :: "photo" :: Nil)) => handleContactPhoto(id, req)
    case req@Path(Seg("events" :: Nil)) => handleEventList(req)
    case req@Path(Seg("events" :: id :: Nil)) => handleEvent(id, req)
    case req@Path(Seg("events" :: eventId :: "publish" :: Nil)) => publish(eventId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: Nil)) => handleSessionList(eventId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: id :: Nil)) => handleSession(eventId, id, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "attachments" :: Nil)) => handleAttachments(eventId, sessionId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: Nil)) => handleSpeakers(eventId, sessionId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: speakerId :: "photo" :: Nil)) => handleSpeakerPhoto(eventId, sessionId, speakerId, req)
    case req@Path(Seg("binary" :: id :: Nil)) => handleAttachment(id, req)
  }

  def handleRoot(req: HttpRequest[HttpServletRequest]) = {
    val builder = BaseURIBuilder.getBuilder(req)

    ContentType("application/xrd+xml") ~> ResponseString(
      <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
         <Link rel="http://rels.java.no/ems/contacts"
                href={builder.segments("contacts").toString}>
           <Title>contacts</Title>
         </Link>
         <Link rel="http://rels.java.no/ems/events"
                href={builder.segments("events").toString}>
           <Title>events</Title>
         </Link>
         <Link rel="http://rels.java.no/ems/binary"
                href={builder.segments("binary").toString}>
           <Title>binary</Title>
         </Link>
      </XRD>.toString()
    )
  }
}

object Main extends App {
  val resources = new MemoryStorage with Resources

  def populate(storage: Storage) {
    val event = storage.saveEvent(Event(Some("1"), "JavaZone 2011", new DateTime(), new DateTime()))
    val sessions = List(Session(event.id.get, "Session 1", Format.Presentation, Vector(Speaker("1", "Erlend Hamnaberg"))).copy(Some("1")), Session(event.id.get, "Session 2", Format.Presentation, Vector()).copy(Some("2")))
    for (s <- sessions) storage.saveSession(s)
    println(event)
  }

  populate(resources)

  unfiltered.jetty.Http(8080).plan(resources).run()
}