package no.java.ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan
import org.joda.time.DateTime
import java.net.URI
import net.hamnaberg.json.generator.JsonCollectionGenerator
import net.hamnaberg.json._
import no.java.ems.converters._

class Resources(storage: Storage) extends Plan {
  private val collgen = new JsonCollectionGenerator()

  implicit def coll2json(collection: JsonCollection): String = collgen.toNode(collection).toString

  def handleEventsList() = {
    implicit val base = URI.create("http://localhost:8080/")
    val output = storage.getEvents().map(eventToItem)
    val curried = list2Collection(base.resolve("/events"), _: List[Item])
    ContentType("text/plain") ~> ResponseString(curried.apply(output))
  }

  def handleEvent(id: String, request: HttpRequest[Any]) = {
    implicit val base = URI.create("http://localhost:8080/")
    request match {
      case GET(_) => storage.getEvent(id).map(eventToItem).map(singleCollection).map(x => {
        ContentType("application/vnd.collection+json") ~> ResponseString(x)
      }).getOrElse(NotFound)
      case _ => NotImplemented
    }
  }


  def handleSessions(eventId: String) = {
    implicit val base = URI.create("http://localhost:8080/")
    val href = base.resolve("/events/%s/sessions".format(eventId))
    ContentType("application/vnd.collection+json") ~> ResponseString(list2Collection(href, storage.getSessions(eventId).map(sessionToItem)))
  }

  def handleSession(eventId: String, sessionId: String, request: HttpRequest[Any]) = {
    request match {
      case GET(_) => {
        implicit val base = URI.create("http://localhost:8080/")
        val session = storage.getSession(eventId, sessionId).map(sessionToItem).map(x => list2Collection(x.getHref, List(x)))
        session.map(x => ContentType("application/vnd.collection+json") ~> ResponseString(x)).getOrElse(NotFound ~> ResponseString("Session was not found"))
      }
      case _ => NotImplemented
    }
  }

  def intent = {
    case GET(Path(Seg("events" :: Nil))) => handleEventsList();
    case req@Path(Seg("events" :: id :: Nil)) => handleEvent(id, req);
    case GET(Path(Seg("events" :: eventId :: "sessions" :: Nil))) => handleSessions(eventId);
    case req@Path(Seg("events" :: eventId :: "sessions" :: id :: Nil)) => handleSession(eventId, id, req);
  }
}

object Main extends App {
  val storage = MemoryStorage()
  val resources = new Resources(storage)

  def populate(storage: Storage) {
    val event = storage.saveEvent(Event(Some("1"), "JavaZone 2011", new DateTime(), new DateTime()))
    val sessions = List(Session(event.id.get, "Session 1"), Session(event.id.get, "Session 2"))
    for (s <- sessions) storage.saveSession(s)
    println(event)
  }

  populate(storage)

  unfiltered.jetty.Http(8080).plan(resources).run()
}