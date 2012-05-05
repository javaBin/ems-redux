package no.java.ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan
import org.joda.time.DateTime
import net.hamnaberg.json.collection._
import no.java.ems.converters._
import javax.servlet.http.HttpServletRequest
import no.java.http.URIBuilder
import no.java.unfiltered._
import io.Source
import java.net.URI

trait Resources extends Plan with EventResources with ContactResources with AttachmentHandler { this: Storage =>

  def intent = {
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