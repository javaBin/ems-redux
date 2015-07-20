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
import ems.cj.LinkCount

trait EventResources extends SessionResources with SpeakerResources {

  def handleSlots(eventId: UUID, parent: Option[UUID] = None)(implicit user: User) = {
    val get = for {
      _ <- GET
      _ <- commit
      e <- getOrElse(storage.getEvent(eventId), NotFound)
      req <- requestURIBuilder
      base <- baseURIBuilder
    } yield {
      val slots = storage.getSlots(eventId, parent)
      val items = slots.map(slotToItem(base, eventId))
      CollectionJsonResponse(JsonCollection(req.emptyParams().build(), Nil, items.toList, Nil, Some(makeTemplate("start", "duration"))))
    }

    val post = createObject[Slot](
      toSlot(_: Template, eventId, parent, None),
      storage.saveSlot(_ : Slot),
      (s: Slot) => List("events", eventId, "slots", s.id.get),
      (s: Slot) => Nil
    )
    get | post
  }

  def handleSlot(eventId: UUID, id: UUID, parent: Option[UUID] = None)(implicit user: User) = for {
    slot <- getOrElse(storage.getSlot(id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Some(slot), (t: Template) => toSlot(t, eventId, parent, Some(id)),
      storage.saveSlot(_ : Slot),
      slotToItem(base, eventId))(identity)
  } yield res

  def handleRooms(id: UUID)(implicit user: User) = {
    val get = for {
      _ <- GET
      _ <- commit
      e <- getOrElse(storage.getEvent(id), NotFound)
      base <- baseURIBuilder
    } yield {
      val items = storage.getRooms(id).map(roomToItem(base, id))
      val href = base.segments("events", id, "rooms").build()
      CollectionJsonResponse(JsonCollection(href, Nil, items.toList, Nil, Some(makeTemplate("name"))))
    }

    val post = createObject[Room](
      toRoom(_: Template, id, None),
      storage.saveRoom(id, _ : Room),
      (r: Room) => List("events", id, "rooms", r.id.get),
      (r: Room) => Nil
    )
    get | post
  }

  def handleRoom(eventId: UUID, id: UUID)(implicit user: User) = for {
    room <- getOrElse(storage.getRoom(eventId, id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Some(room), (t: Template) => toRoom(t, eventId, Some(id)),
      storage.saveRoom(eventId, _ : Room),
      roomToItem(base, eventId))(identity)
  } yield res


  def handleEventList(implicit user:User): ResponseDirective = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      p <- queryParams
    } yield {
      p("slug").headOption match {
        case Some(s) => storage.getEventsBySlug(s).headOption.map(e => Found ~> Location(base.segments("events", e.id.get).toString)).getOrElse(NotFound)
        case None => {
          val events = storage.getEventsWithSessionCount(user)
          val items = events.map{evt =>
            val item = eventToItem(base)(evt.event)
            item.withLinks(item.links.collect{
              case l if l.rel == "session collection" => l.apply(LinkCount, evt.count)
              case x => x
            })
          }
          val href = base.segments("events").build()
          CacheControl("public, max-age=" + Config.cache.events) ~> CollectionJsonResponse(JsonCollection(href, Nil, items.toList, Nil, Some(makeTemplate("name", "venue"))))
        }
      }
    }
    val post = createObject[Event](
      toEvent(_: Template, None),
      storage.saveEvent,
      (e: Event) => List("events", e.id.get),
      (e: Event) => Nil
    )

    get | post
  }

  def handleEvent(id: UUID)(implicit user:User) = for {
    event <- getOrElse(storage.getEvent(id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Some(event), (t: Template) => toEvent(t, Some(id)), storage.saveEvent, eventToItem(base)) {
      c => c.addQuery(Query(URIBuilder(c.href).segments("sessions").build(), "session by-slug", List(
         ValueProperty("slug")
      ), Some("Session by Slug")))
    }
  } yield res
}
