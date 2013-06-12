package ems

import config.Config
import model._
import ems.converters._
import util.URIBuilder
import security.User
import unfiltered.response._
import unfiltered.request._
import unfiltered.directives._
import Directives._
import net.hamnaberg.json.collection._
import net.hamnaberg.json.collection.Template

trait EventResources extends SessionResources with SpeakerResources {

  def handleSlots(id: String)(implicit user: User) = {
    val get = for {
      _ <- autocommit(GET)
      e <- getOrElse(storage.getEvent(id), NotFound)
      base <- baseURIBuilder
    } yield {
      val items = e.slots.map(slotToItem(base, id))
      val href = base.segments("events", id, "slots").build()
      CollectionJsonResponse(JsonCollection(href, Nil, items.toList, Nil, Some(makeTemplate("start", "end"))))
    }

    val post = createObject[Slot](toSlot(_: Template, None), storage.saveSlot(id, _ : Slot), (s: Slot) => List("events", id, "slots", s.id.get))
    get | post
  }

  def handleRooms(id: String)(implicit user: User) = {
    val get = for {
      _ <- autocommit(GET)
      e <- getOrElse(storage.getEvent(id), NotFound)
      base <- baseURIBuilder
    } yield {
      val items = e.rooms.map(roomToItem(base, id))
      val href = base.segments("events", id, "rooms").build()
      CollectionJsonResponse(JsonCollection(href, Nil, items.toList, Nil, Some(makeTemplate("name"))))
    }

    val post = createObject[Room](toRoom(_: Template, None), storage.saveRoom(id, _ : Room), (r: Room) => List("events", id, "rooms", r.id.get))
    get | post
  }

  def handleEventList(implicit user:User) = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      p <- queryParams
    } yield {
      p("slug").headOption match {
        case Some(s) => storage.getEventsBySlug(s).headOption.map(e => Found ~> Location(base.segments("events", e.id.get).toString)).getOrElse(NotFound)
        case None => {
          val events = storage.getEvents()
          val items = events.map(eventToItem(base))
          val href = base.segments("events").build()
          CacheControl("public, max-age=" + Config.cache.events) ~> CollectionJsonResponse(JsonCollection(href, Nil, items, Nil, Some(makeTemplate("name", "venue"))))
        }
      }
    }
    val post = createObject[Event](toEvent(_: Template, None), storage.saveEvent _, (e: Event) => List("events", e.id.get))

    get | post
  }

  def handleEvent(id: String)(implicit user:User) = for {
    event <- getOrElse(storage.getEvent(id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Some(event), (t: Template) => toEvent(t, Some(id)), storage.saveEvent _, eventToItem(base)) {
      c => c.addQuery(Query(URIBuilder(c.href).segments("sessions").build(), "session by-slug", List(
         ValueProperty("slug")
      ), Some("Session by Slug")))
    }
  } yield res
}