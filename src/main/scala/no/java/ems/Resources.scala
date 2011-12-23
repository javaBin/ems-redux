package no.java.ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.request.Accepts
import unfiltered.filter.Plan
import org.joda.time.DateTime
import net.hamnaberg.json.collection._
import no.java.ems.converters._
import no.java.unfiltered.{BaseURIBuilder, RequestURIBuilder}
import javax.servlet.http.HttpServletRequest

class Resources(storage: Storage) extends Plan {

  def handleEventsList(request: HttpRequest[HttpServletRequest]) = {
    val baseUriBuilder = BaseURIBuilder.unapply(request).get
    val output = storage.getEvents().map(eventToItem(baseUriBuilder))
    val href = baseUriBuilder.segments("events").build()
    CollectionJsonResponse(JsonCollection(href, Nil, output))
  }

  def handleEvent(id: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(req) => {
        val baseUriBuilder = BaseURIBuilder.unapply(req).get
        storage.getEvent(id).map(eventToItem(baseUriBuilder)).map(singleCollection).map(CollectionJsonResponse(_)).getOrElse(NotFound)
      }
      case _ => NotImplemented
    }
  }


  def handleSessions(eventId: String, request: HttpRequest[HttpServletRequest]) = {
    val baseUriBuilder = BaseURIBuilder.unapply(request).get
    val href = baseUriBuilder.segments("events", eventId, "sessions").build()
    val items = storage.getSessions(eventId).map(sessionToItem(baseUriBuilder))
    CollectionJsonResponse(JsonCollection(href, Nil, items))
  }

  def handleSession(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest]) = {
    val baseUriBuilder = BaseURIBuilder.unapply(request).get
    request match {
      case GET(_) => {
        storage.getSession(eventId, sessionId).map(sessionToItem(baseUriBuilder)).map(singleCollection) match {
          case Some(x) => CollectionJsonResponse(x)
          case None => NotFound ~> ResponseString("Session was not found")
        }
      }
      case _ => NotImplemented
    }
  }

  def intent = {
    case req@GET(Path(Seg("events" :: Nil))) => handleEventsList(req);
    case req@Path(Seg("events" :: id :: Nil)) => handleEvent(id, req);
    case req@GET(Path(Seg("events" :: eventId :: "sessions" :: Nil))) => handleSessions(eventId, req);
    case req@Path(Seg("events" :: eventId :: "sessions" :: id :: Nil)) => handleSession(eventId, id, req);
  }
}

object CollectionJsonResponse {
  import net.liftweb.json._
  def apply(coll: JsonCollection) = {
    println(coll.toJson)
    new ComposeResponse[Any](ContentType("application/vnd.collection+json") ~> ResponseString(compact(render(coll.toJson))))
  }
}

object AcceptCollectionJson extends Accepts.Accepting {
  val contentType = "application/vnd.collection+json"
  val ext = "json"

  override def unapply[T](r: HttpRequest[T]) = Accepts.Json.unapply(r) orElse super.unapply(r)
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