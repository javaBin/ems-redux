package ems

import ems.storage.DBStorage
import ems.security.{JAASAuthenticator, User, Authenticator}
import ems.Links._

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import net.hamnaberg.json.collection.{ValueProperty, Query, Link, JsonCollection}
import org.joda.time.DateTime

import unfiltered.directives._
import unfiltered.directives.Directives._

import unfiltered.filter.Plan
import unfiltered.filter.request.ContextPath
import unfiltered.request._
import unfiltered.response._

import linx._

import org.json4s.native.JsonMethods._

class Resources(override val storage: DBStorage, auth: Authenticator[HttpServletRequest, HttpServletResponse]) extends Plan with EventResources with AttachmentHandler {

  case class Mapping[X](from: HttpRequest[HttpServletRequest] => X) {
    def apply(intent: PartialFunction[X, Directive[HttpServletRequest, ResponseFunction[Any], ResponseFunction[Any]]]): unfiltered.Cycle.Intent[HttpServletRequest, Any] =
      Directive.Intent {
        case req if intent.isDefinedAt(from(req)) => intent(from(req))
      }
  }


  val Intent = Mapping[String]{ case ContextPath(_, path) => path }

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
    case SessionsChangelog(eventId) => handleChangelog(eventId)
    case Session(eventId, id) => handleSessionAndForms(eventId, id)
    case SessionRoom(eventId, sessionId) => handleSessionRoom(eventId, sessionId)
    case SessionAttachments(eventId, sessionId) => failure(NotFound)
    case Speakers(eventId, sessionId) => handleSpeakers(eventId, sessionId)
    case Speaker(eventId, sessionId, speakerId) => handleSpeaker(eventId, sessionId, speakerId)
    case SpeakerPhoto(eventId, sessionId, speakerId) => handleSpeakerPhoto(eventId, sessionId, speakerId)
    case Binary(id) => handleAttachment(id)
    case Seg("redirect" :: Nil) => handleRedirect
    case Seg("app-info" :: Nil) => {
      for {
        _ <- GET
        res <- getOrElse(handleAppInfo, NotFound)
      } yield {
        res
      }
    }
    case _ => failure(NotFound)
  }

  def handleRedirect(implicit user: User) = for {
    _ <- GET
    base <- baseURIBuilder
    params <- QueryParams
  } yield {
    val eventSlug = params("event-slug").headOption
    val sessionSlug = params("session-slug").headOption
    val result = (eventSlug, sessionSlug) match {
      case (Some(e), Some(s)) => {
        for {
          event <- storage.getEventsBySlug(e).headOption
          id <- event.id
          session <- storage.getSessionsBySlug(id, s).headOption
        } yield {
          Found ~> CacheControl("max-age=3600") ~> Location(base.segments("events", id, "sessions", session.id.get).build().toString)
        }
      }
      case (Some(e), None) => {
        for {
          event <- storage.getEventsBySlug(e).headOption
          id <- event.id
        } yield {
          Found ~> CacheControl("max-age=3600") ~> Location(base.segments("events", id).build().toString)
        }
      }
      case _ => None
    }
    result.getOrElse(BadRequest)
  }

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
        Query(base.segments("changelog").build(), "changelog", List(
          ValueProperty("type", Some("Type")),
          ValueProperty("from", Some("From DateTime"))
        ), Some("Changelog")),
        Query(base.segments("events").build(), "event by-slug", List(
          ValueProperty("slug", Some("Slug"))
        ), Some("Event By Slug")),
        Query(base.segments("redirect").build(), "event session by-slug", List(
        ValueProperty("event-slug", Some("Slug")),
        ValueProperty("session-slug", Some("Slug"))
      ), Some("Event or Session By Slug"))
    )))
  }

  private def handleAppInfo = scala.util.Try {
    import org.json4s._

    val dbStatus = storage.status()

    val json = JObject(
     "build-info" -> JObject(
       "sha" -> JString(ems.BuildInfo.sha),
       "version" -> JString(ems.BuildInfo.version),
       ("build-time", JString(new DateTime(ems.BuildInfo.buildTime).toString()))
     ),
     "db-connection" -> JString(dbStatus)
    )

    val returnCode = if (dbStatus == "ok") Ok else InternalServerError
    returnCode ~> ContentType("application/json") ~> ResponseString(compact(render(json)) + "\n")

  }.toOption
}

object Resources {
  def apply(storage: DBStorage, authenticator: Authenticator[HttpServletRequest, HttpServletResponse]) = new Resources(storage, authenticator)

}
