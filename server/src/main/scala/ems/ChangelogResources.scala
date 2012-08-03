package no.java.ems

import no.java.unfiltered.{RequestURIBuilder, BaseURIBuilder}
import security.User
import storage.MongoDBStorage
import unfiltered.request._
import unfiltered.response._
import javax.servlet.http.HttpServletRequest
import scala.util.control.Exception._
import net.hamnaberg.json.collection.{ErrorMessage, JsonCollection}

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait ChangelogResources {
  def storage: MongoDBStorage

  def handleChangelog(req: HttpRequest[HttpServletRequest])(implicit u: Option[User]) = {
    req match {
      case GET(Params(p)) & BaseURIBuilder(b) & RequestURIBuilder(r) => {
        val query = p("type").headOption ->
          p("from").headOption.flatMap(s => allCatch.opt(converters.DateFormat.parseDateTime(s)))

        val items = query match {
          case (Some("event"), Some(dt)) => Right(storage.getChangedEvents(dt).map(converters.eventToItem(b)))
          case (Some("session"), Some(dt)) => Right(storage.getChangedSessions(dt).map(converters.sessionToItem(b)))
          case (Some("contact"), Some(dt)) => Right(storage.getChangedContacts(dt).map(converters.contactToItem(b)))
          case (Some(_), None) => Left(ErrorMessage("Missing or malformed from date", None, None))
          case (None, Some(_)) => Left(ErrorMessage("Unknown entity", None, None))
          case (None, None) => Left(ErrorMessage("Missing entity and from date", None, None))
        }
        items.fold(
          msg => BadRequest ~> CollectionJsonResponse(JsonCollection(r.build(), msg)),
          it => {
          CollectionJsonResponse(JsonCollection(r.build(), Nil, it.toList)) ~> CacheControl("max-age=5,no-transform")
        })
      }
      case _ => MethodNotAllowed
    }
  }
}
