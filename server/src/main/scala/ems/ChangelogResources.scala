package ems

import security.User
import unfiltered.request._
import unfiltered.response._
import unfiltered.directives._
import Directives._
import net.hamnaberg.json.collection.{Error, JsonCollection}
import util.RFC3339


trait ChangelogResources extends ResourceHelper {

  def handleChangelog(implicit u: User) = for {
    _ <- autocommit(GET)
    _ <- authenticated(u)
    base <- baseURIBuilder
    href <- requestURI
    p <- queryParams
  } yield {
    val query = p("type").headOption ->
      p("from").headOption.toRight("Missing from date").right.flatMap(s => RFC3339.parseDateTime(s))

    val items = query match {
      case (Some("event"), Right(dt)) => Right(storage.getChangedEvents(dt).map(converters.eventToItem(base)))
      case (Some("session"), Right(dt)) => Right(storage.getChangedSessions(dt).map(converters.sessionToItem(base)))
      case (Some(_), Left(e)) => Left(Error(e, None, None))
      case (Some(_), Right(e)) => Left(Error("Unknown entity", None, None))
      case (None, Right(_)) => Left(Error("Unknown entity", None, None))
      case (None, Left(e)) => Left(Error("Missing entity and " + e, None, None))
    }
    items.fold(
      msg => BadRequest ~> CollectionJsonResponse(JsonCollection(href, msg)),
      it => {
        CollectionJsonResponse(JsonCollection(href, Nil, it.toList)) ~> CacheControl("max-age=5,no-transform")
      })
  }
}
