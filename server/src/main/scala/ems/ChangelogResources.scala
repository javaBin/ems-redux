package no.java.ems

import no.java.unfiltered.{RequestURIBuilder, BaseURIBuilder}
import security.User
import storage.MongoDBStorage
import unfiltered.request._
import unfiltered.response._
import javax.servlet.http.HttpServletRequest
import scala.util.control.Exception._
import net.hamnaberg.json.collection.{ErrorMessage, JsonCollection}
import util.RFC3339


trait ChangelogResources {
  def storage: MongoDBStorage

  def handleChangelog(req: HttpRequest[HttpServletRequest])(implicit u: User) = {
    req match {
      case GET(Params(p)) & BaseURIBuilder(b) & RequestURIBuilder(r) => {
        val query = p("type").headOption ->
          p("from").headOption.toRight("Missing from date").right.flatMap(s => RFC3339.parseDateTime(s))

        val items = query match {
          case (Some("event"), Right(dt)) => Right(storage.getChangedEvents(dt).map(converters.eventToItem(b)))
          case (Some("session"), Right(dt)) => Right(storage.getChangedSessions(dt).map(converters.sessionToItem(b)))
          case (Some(_), Left(e)) => Left(ErrorMessage(e, None, None))
          case (None, Right(_)) => Left(ErrorMessage("Unknown entity", None, None))
          case (None, Left(e)) => Left(ErrorMessage("Missing entity and " + e, None, None))
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
