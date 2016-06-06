package ems

import d2.Async
import ems.storage.DBStorage
import ems.security.{Authenticator, User}
import ems.Links._
import net.hamnaberg.json.collection.{JsonCollection, Link, Query, ValueProperty}
import org.joda.time.DateTime
import unfiltered.filter.async.Plan
import unfiltered.filter.request.ContextPath
import unfiltered.request._
import unfiltered.response._
import linx._
import org.json4s.native.JsonMethods._
import sangria.schema.Schema

import scala.concurrent.duration.Duration
import scala.concurrent._
import scala.util.Try

class Resources[A, B](
    override val storage: DBStorage,
    override val emsSchema: Schema[Unit, Unit],
    auth: Authenticator[A, B],
    override val ec: ExecutionContext)
    extends Plan with EventResources with AttachmentHandler with GraphQlResource {
  import Directives._
  import ops._

  val Intent = Async.Mapping[Any, String]{ case ContextPath(_, path) => path }

  import auth._

  def intent = {
    case Authenticated(uf) => uf(u => withUser(u))
  }

  private def withUser(implicit user: User) = Intent {
    case Root() => handleRoot
    case Events() => handleEventList
    case Event(id) => handleEvent(id)
    case Slots(eventId) => handleSlots(eventId)
    case Slot(eventId, slotId) => handleSlot(eventId, slotId)
    case SlotChildren(eventId, slotId) => handleSlots(eventId, Some(slotId))
    case Rooms(eventId) => handleRooms(eventId)
    case Sessions(eventId) => handleSessionList(eventId)
    case SessionsTags(eventId) => handleAllTags(eventId)
    case Session(eventId, id) => handleSessionAndForms(eventId, id)
    case SessionRoom(eventId, sessionId) => handleSessionRoom(eventId, sessionId)
    case SessionAttachments(eventId, sessionId) => failure(NotFound)
    case Speakers(eventId, sessionId) => handleSpeakers(eventId, sessionId)
    case Speaker(eventId, sessionId, speakerId) => handleSpeaker(eventId, sessionId, speakerId)
    case SpeakerPhoto(eventId, sessionId, speakerId) => handleSpeakerPhoto(eventId, sessionId, speakerId)
    case Binary(id) => handleAttachment(id)
    case GraphQl() => handleGraphQl
    //case Seg("redirect" :: Nil) => handleRedirect
    case Seg("app-info" :: Nil) => {
      for {
        _ <- GET
        res <- handleAppInfo
      } yield {
        res
      }
    }
    case _ => failure(NotFound)
  }

  /*def handleRedirect(implicit user: User) = for {
    _ <- GET
    base <- baseURIBuilder
    params <- QueryParams
  } yield {
    val eventSlug = params("event-slug").headOption
    val sessionSlug = params("session-slug").headOption
    val result = (eventSlug, sessionSlug) match {
      case (Some(e), Some(s)) => {
        for {
          eventId <- storage.getEventBySlug(e)
          sessionId <- storage.getSessionBySlug(eventId, s)
        } yield {
          SeeOther ~> CacheControl("max-age=3600") ~> Location(base.segments("events", eventId, "sessions", sessionId).build().toString)
        }
      }
      case (Some(e), None) => {
        for {
          eventId <- storage.getEventBySlug(e)
        } yield {
          SeeOther ~> CacheControl("max-age=3600") ~> Location(base.segments("events", eventId).build().toString)
        }
      }
      case _ => None
    }
    result.getOrElse(BadRequest)
  }*/

  def handleRoot = for {
    base <- baseURIBuilder
  } yield {
    CacheControl("max-age=3600") ~> CollectionJsonResponse(JsonCollection(
      base.build(),
      List(
        Link(base.segments("events").build(), "event collection")
      ),
      Nil,
      List(
        Query(base.segments("events").build(), "event by-slug", List(
          ValueProperty("slug", Some("Slug"))
        ), Some("Event By Slug")),
        Query(base.segments("redirect").build(), "event session by-slug", List(
        ValueProperty("event-slug", Some("Slug")),
        ValueProperty("session-slug", Some("Slug"))
      ), Some("Event or Session By Slug"))
    )))
  }

  private def handleAppInfo: ResponseDirective = {
    import org.json4s._

    val triedString: Try[String] = Try(Await.result(storage.status(), Duration(1, duration.SECONDS)))
    val status: String = triedString.recover{
      case x: TimeoutException => "Could not get connection within 1 second"
      case x: Throwable => x.getMessage
    }.getOrElse("unknown status")

    val json = JObject(
     "build-info" -> JObject(
       "sha" -> JString(ems.BuildInfo.sha),
       "version" -> JString(ems.BuildInfo.version),
       ("build-time", JString(new DateTime(ems.BuildInfo.buildTime).toString()))
     ),
     "db-connection" -> JString(status)
    )

    val returnCode = if (status == "ok") Ok else InternalServerError
    success(returnCode ~> ContentType("application/json") ~> ResponseString(compact(render(json)) + "\n"))
  }

}

object Resources {
  def apply[A, B](storage: DBStorage, schema: Schema[Unit, Unit], authenticator: Authenticator[A, B])(implicit ec: ExecutionContext) =
    new Resources(storage, schema, authenticator, ec)

}
