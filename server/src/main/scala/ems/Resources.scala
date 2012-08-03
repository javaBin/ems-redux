package no.java.ems

import security.{User, Authenticator}
import storage.{MongoSetting, MongoDBStorage}
import unfiltered.request._
import unfiltered.filter.Plan
import javax.servlet.http.HttpServletRequest
import no.java.unfiltered._
import scala.util.Properties
import net.hamnaberg.json.collection.{ValueProperty, Query, Link, JsonCollection}

class Resources(override val storage: MongoDBStorage, auth: Authenticator) extends Plan with EventResources with ContactResources with AttachmentHandler with ChangelogResources {
  import auth._

  def intent = {
    case Authenticated(f) => f((u: Option[User]) => pathMapper(u))
  }

  private def pathMapper(implicit u: Option[User]): Plan.Intent = {
    case req@Path(Seg(Nil)) => handleRoot(req)
    case req@Path(Seg("changelog" :: Nil)) => handleChangelog(req)
    case req@Path(Seg("contacts" :: Nil)) => handleContactList(req)
    case req@Path(Seg("contacts" :: id :: Nil)) => handleContact(id, req)
    case req@Path(Seg("contacts" :: id :: "photo" :: Nil)) => handleContactPhoto(id, req)
    case req@Path(Seg("events" :: Nil)) => handleEventList(req)
    case req@Path(Seg("events" :: id :: Nil)) => handleEvent(id, req)
    case req@Path(Seg("events" :: eventId :: "publish" :: Nil)) => publish(eventId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: Nil)) => handleSessionList(eventId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: id :: Nil)) => handleSession(eventId, id, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "attachments" :: Nil)) => handleSessionAttachments(eventId, sessionId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: Nil)) => handleSpeakers(eventId, sessionId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "speakers" :: speakerId :: "photo" :: Nil)) => handleSpeakerPhoto(eventId, sessionId, speakerId, req)
    case req@Path(Seg("binary" :: id :: Nil)) => handleAttachment(id, req)
  }

  def handleRoot(req: HttpRequest[HttpServletRequest]) = {
    val builder = BaseURIBuilder.getBuilder(req)
    CollectionJsonResponse(JsonCollection(
      RequestURIBuilder.getBuilder(req).emptyParams().build(),
      List(
        Link(builder.segments("contacts").build(), "contact collection"),
        Link(builder.segments("events").build(), "event collection")
      ),
      Nil,
      List(Query(builder.segments("changelog").build(), "changelog", Some("Changelog"), List(
        ValueProperty("type", Some("Type")),
        ValueProperty("from", Some("From DateTime"))
      )))
    ))
  }
}

object Resources {
  object storage extends MongoDBStorage {
    val MongoSetting(db) = Properties.envOrNone("MONGOLAB_URI")
  }

  def apply(authenticator: Authenticator) = new Resources(storage, authenticator)

}
