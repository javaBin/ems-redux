package ems

import security.{JAASAuthenticator, User, Authenticator}
import storage.{MongoSetting, MongoDBStorage}
import unfiltered.request._
import unfiltered.filter.Plan
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import net.hamnaberg.json.collection.{ValueProperty, Query, Link, JsonCollection}
import unfiltered.filter.request.ContextPath
import unfiltered.response._
import ems.storage.FilesystemBinaryStorage
import ems.config.Config
import unfiltered.directives.{Result, Directive}
import unfiltered.directives.Directives._

class Resources(override val storage: MongoDBStorage, auth: Authenticator[HttpServletRequest, HttpServletResponse]) extends Plan with EventResources with AttachmentHandler with ChangelogResources {
  val Intent = Directive.Intent[HttpServletRequest, String]{ case ContextPath(_, path) => path }

  import auth._

  def this() = this(Resources.storage, JAASAuthenticator)

  def intent = {
    case Authenticated(f) => f((u: User) => Intent(pathMapper(u)))
  }

  private def pathMapper(implicit user: User): PartialFunction[String, HttpRequest[HttpServletRequest] => Result[Any, ResponseFunction[Any]]] = {
    case Seg(Nil) => handleRoot
    case Seg("changelog" :: Nil) => handleChangelog
    case Seg("events" :: Nil) => handleEventList
    case Seg("events" :: id :: Nil) => handleEvent(id)
    case Seg("events" :: eventId :: "slots" :: Nil) => handleSlots(eventId)
    case Seg("events" :: eventId :: "tags" :: Nil) => handleAllTags(eventId)
    case Seg("events" :: eventId :: "rooms" :: Nil) => handleRooms(eventId)
    case Seg("events" :: eventId :: "sessions" :: Nil) => handleSessionList(eventId)
    case Seg("events" :: eventId :: "sessions" :: id :: Nil) => handleSession(eventId, id)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "slot" :: Nil) => handleSessionSlot(eventId, sessionId)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "tags" :: Nil) => handleSessionTags(eventId, sessionId)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "room" :: Nil) => handleSessionRoom(eventId, sessionId)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "attachments" :: Nil) => handleSessionAttachments(eventId, sessionId)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: Nil) => handleSpeakers(eventId, sessionId)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: speakerId :: Nil) => handleSpeaker(eventId, sessionId, speakerId)
    case Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: speakerId :: "photo" :: Nil) => handleSpeakerPhoto(eventId, sessionId, speakerId)
    case Seg("binary" :: id :: Nil) => handleAttachment(id)
    case Seg("redirect" :: Nil) => handleRedirect
    case _ => unfiltered.directives.Directives.failure(NotFound)
  }

  val handleRedirect = for {
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
}

object Resources {
  object storage extends MongoDBStorage {
    val MongoSetting(db) = Some(Config.server.mongo)
    val binary = new FilesystemBinaryStorage(Config.server.binary)
  }

  def apply(authenticator: Authenticator[HttpServletRequest, HttpServletResponse]) = new Resources(storage, authenticator)

}
