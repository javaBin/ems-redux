package no.java.ems

import javax.servlet.http.HttpServletRequest
import model.{Speaker, Entity}
import no.java.ems.converters._
import java.net.URI
import no.java.util.URIBuilder
import io.Source
import security.User
import unfiltered.response._
import unfiltered.request._
import no.java.unfiltered.{RequestURIBuilder, RequestContentDisposition, BaseURIBuilder}
import net.hamnaberg.json.collection._

trait EventResources extends ResourceHelper with SessionResources with SpeakerResources {

  def handleSlots(id: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val items = storage.getEvent(id).map(_.slots.map(slotToItem(baseUriBuilder, id))).getOrElse(Nil)
        val href = baseUriBuilder.segments("events", id, "slots").build()
        CollectionJsonResponse(JsonCollection(href, Nil, items.toList))
      }
      case POST(_) => {
        authenticated(request, user) {
          case req@RequestContentType(CollectionJsonResponse.contentType) & BaseURIBuilder(baseUriBuilder) => {
            withTemplate(req) {
              t => {
                storage.saveSlot(id, toSlot(t, None)).fold(
                  ex => InternalServerError ~> ResponseString(ex.getMessage),
                  stored => Created ~> Location(baseUriBuilder.segments("events", "slots", stored.id.get).build().toString)
                )
              }
            }
          }
          case _ => UnsupportedMediaType
        }
      }
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
     case POST(_) => {
       authenticated(request, user) {
         case req@RequestContentType(CollectionJsonResponse.contentType) & BaseURIBuilder(baseUriBuilder) => {
           withTemplate(req) {
             t => {
               storage.saveRoom(id, toRoom(t, None)).fold(
                 ex => InternalServerError ~> ResponseString(ex.getMessage),
                 stored => Created ~> Location(baseUriBuilder.segments("events", "slots", stored.id.get).build().toString)
               )
             }
           }
         }
         case _ => UnsupportedMediaType
       }
      }
      case _ => MethodNotAllowed
    }
  }

  def handleEventList(request: HttpRequest[HttpServletRequest])(implicit user:User) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) & Params(p) => {
        val byName = p("slug").headOption
        val events = byName.map(storage.getEventsBySlug(_)).getOrElse(storage.getEvents)
        val items = events.map(eventToItem(baseUriBuilder))
        val href = baseUriBuilder.segments("events").build()
        CollectionJsonResponse(JsonCollection(href, Nil, items))
      }
      case POST(_) => {
        authenticated(request, user) {
          case req@RequestContentType(CollectionJsonResponse.contentType) & BaseURIBuilder(baseUriBuilder) => {
            withTemplate(req) {
              t => {
                val e = toEvent(None, t)
                storage.saveEvent(e).fold(
                  ex => InternalServerError ~> ResponseString(ex.getMessage),
                  stored => Created ~> Location(baseUriBuilder.segments("events", stored.id.get).build().toString)
                )
              }
            }
          }
          case _ => UnsupportedMediaType
        }
      }
      case _ => MethodNotAllowed
    }
  }

  def handleEvent(id: String, request: HttpRequest[HttpServletRequest])(implicit user:User) = {
    val event = storage.getEvent(id)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(event, request, (t: Template) => toEvent(Some(id), t), storage.saveEvent, eventToItem(base))
  }
}