package ems

import model._
import ems.converters._
import util.URIBuilder
import security.User
import unfiltered.response._
import unfiltered.request._
import net.hamnaberg.json.collection._
import net.hamnaberg.json.collection.Template
import ems.cj.LinkCount
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EventResources extends SessionResources with SpeakerResources {
  import Directives._
  import ops._

  def handleSlots(eventId: UUID, parent: Option[UUID] = None)(implicit user: User) = {
    val get = for {
      _ <- GET
      _ <- commit
      e <- getOrElseF(storage.getEvent(eventId), NotFound)
      req <- requestURIBuilder
      base <- baseURIBuilder
      slots <- storage.getSlots(eventId, parent).successValue
    } yield {
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
    slot <- getOrElseF(storage.getSlot(eventId, id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Future.successful(Some(slot)), (t: Template) => toSlot(t, eventId, parent, Some(id)),
      storage.saveSlot(_ : Slot),
      slotToItem(base, eventId))(identity)
  } yield res

  def handleRooms(eventId: UUID)(implicit user: User): ResponseDirective = {
    val get = for {
      _ <- GET
      _ <- commit
      e <- getOrElseF(storage.getEvent(eventId), NotFound)
      base <- baseURIBuilder
      rooms <- storage.getRooms(eventId).successValue

    } yield {
      val items = rooms.map(roomToItem(base, eventId))
      val href = base.segments("events", eventId, "rooms").build()
      CollectionJsonResponse(JsonCollection(href, Nil, items.toList, Nil, Some(makeTemplate("name"))))
    }

    val post = createObject[Room](
      toRoom(_: Template, eventId, None),
      storage.saveRoom(eventId, _ : Room),
      (r: Room) => List("events", eventId, "rooms", r.id.get),
      (r: Room) => Nil
    )
    get | post
  }

  def handleRoom(eventId: UUID, id: UUID)(implicit user: User) = for {
    room <- getOrElseF(storage.getRoom(eventId, id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Future.successful(Some(room)), (t: Template) => toRoom(t, eventId, Some(id)),
      storage.saveRoom(eventId, _ : Room),
      roomToItem(base, eventId))(identity)
  } yield res


  def handleEventList(implicit user:User): ResponseDirective = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      p <- queryParams
      res <- handleSlug(base, p("slug").headOption).successValue
    } yield {
      res
    }
    val post = createObject[Event](
      toEvent(_: Template, None),
      storage.saveEvent,
      (e: Event) => List("events", e.id.get),
      (e: Event) => Nil
    )

    get | post
  }

  def handleSlug(base: URIBuilder, slug: Option[String])(implicit user: User) = {
    slug match {
      case Some(s) => storage.getEventBySlug(s).map(_.map(eid => SeeOther ~> Location(base.segments("events", eid).toString)).getOrElse(NotFound))
      case None => {
        storage.getEventsWithSessionCount.map{ events =>

          val items = events.map { evt =>
            val item = eventToItem(base)(evt.event)
            item.withLinks(item.links.collect {
              case l if l.rel == "session collection" => l.apply(LinkCount, evt.count)
              case x => x
            })
          }
          val href = base.segments("events").build()
          CollectionJsonResponse(JsonCollection(href, Nil, items.toList, Nil, Some(makeTemplate("name", "venue"))))
        }
      }
    }
  }

  def handleEvent(id: UUID)(implicit user:User) = for {
    event <- getOrElseF(storage.getEvent(id), NotFound)
    base <- baseURIBuilder
    res <- handleObject(Future.successful(Some(event)), (t: Template) => toEvent(t, Some(id)), storage.saveEvent, eventToItem(base)) {
      c => c.addQuery(Query(URIBuilder(c.href).segments("sessions").build(), "session by-slug", List(
         ValueProperty("slug")
      ), Some("Session by Slug")))
    }
  } yield res
}
