package ems

import javax.servlet.http.HttpServletRequest
import model._
import ems.converters._
import java.net.URI
import no.java.util.URIBuilder
import io.Source
import security.User
import unfiltered.response._
import unfiltered.request._
import no.java.unfiltered.{RequestURIBuilder, RequestContentDisposition, BaseURIBuilder}
import net.hamnaberg.json.collection._
import net.hamnaberg.json.collection.Template
import scala.Some

trait EventResources extends ResourceHelper with SessionResources with SpeakerResources {

  def handleSlots(id: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val items = storage.getEvent(id).map(_.slots.map(slotToItem(baseUriBuilder, id))).getOrElse(Nil)
        val href = baseUriBuilder.segments("events", id, "slots").build()
        CollectionJsonResponse(JsonCollection(href, Nil, items.toList))
      }
      case POST(_) => createObject[Slot](request, toSlot(_: Template, None), storage.saveSlot(id, _ : Slot), s => List("events", id, "slots", s.id.get))
      case _ => MethodNotAllowed
    }
  }

  def handleRooms(id: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val items = storage.getEvent(id).map(_.rooms.map(roomToItem(baseUriBuilder, id))).getOrElse(Nil)
        val href = baseUriBuilder.segments("events", id, "rooms").build()
        CollectionJsonResponse(JsonCollection(href, Nil, items.toList))
      }
      case POST(_) => createObject[Room](request, toRoom(_: Template, None), storage.saveRoom(id, _ : Room), r => List("events", id, "rooms", r.id.get))
      case _ => MethodNotAllowed
    }
  }

  def handleEventList(request: HttpRequest[HttpServletRequest])(implicit user:User) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) & Params(p) => {
        p("slug").headOption match {
          case Some(s) => storage.getEventsBySlug(s).headOption.map(e => Found ~> Location(baseUriBuilder.segments("events", e.id.get).toString)).getOrElse(NotFound)
          case None => {
            val events = storage.getEvents()
            val items = events.map(eventToItem(baseUriBuilder))
            val href = baseUriBuilder.segments("events").build()
            CollectionJsonResponse(JsonCollection(href, Nil, items))
          }
        }
      }
      case POST(_) => createObject[Event](request, toEvent(_: Template, None), storage.saveEvent, e => List("events", e.id.get))
      case _ => MethodNotAllowed
    }
  }

  def handleEvent(id: String, request: HttpRequest[HttpServletRequest])(implicit user:User) = {
    val event = storage.getEvent(id)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(event, request, (t: Template) => toEvent(t, Some(id)), storage.saveEvent, eventToItem(base)) {
      c => c.addQuery(Query(URIBuilder(c.href).segments("sessions").build(), "session by-slug", Some("Session by Slug"), List(
         ValueProperty("slug")
      )))
    }
  }
}