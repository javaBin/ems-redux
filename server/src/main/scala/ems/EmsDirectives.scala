package ems

import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import unfiltered.directives._
import unfiltered.directives.Result._
import Directives._
import unfiltered.request._
import unfiltered.response._
import unfilteredx._
import net.hamnaberg.json.collection.{Error, JsonCollection}

trait EmsDirectives {
  def baseURIBuilder = request[HttpServletRequest].map(r => BaseURIBuilder(r))

  def baseURI = request[HttpServletRequest].map(r => BaseURI(r))

  def requestURIBuilder = request[Any].map(r => RequestURIBuilder(r))

  def requestURI = request[Any].map(r => RequestURI(r))

  def ifModifiedSince(dt: DateTime, res: ResponseFunction[Any]) = commit(when {
    case IfModifiedSince(date) if (dt.withMillisOfSecond(0).toDate != date) => res
    case _ => res
  }.orElse(NotModified))

  def ifUnmodifiedSince[A](dt: DateTime) = Directive[A, Any, Unit]{
    case IfUnmodifiedSinceString("*") => Success(())
    case IfUnmodifiedSince(date) if (dt.withMillisOfSecond(0).toDate == date) => Success(())
    case IfUnmodifiedSince(_) => Result.Error(PreconditionFailed)
    case RequestURI(href) => Result.Error(PreconditionRequired ~> CollectionJsonResponse(JsonCollection(href, Error("Wrong response", None, Some("Missing If-Unmodified-Since header")))))
  }

  def contentType(ct: String) = commit(when {
    case RequestContentType(`ct`) => ct
  }.orElse(UnsupportedMediaType))

  def contentDisposition = commit(
    for {
      href <- requestURI
      res <- when {
        case RequestContentDisposition(cd) => cd
      }.orElse(BadRequest ~> CollectionJsonResponse(JsonCollection(href, Error("Wrong response", None, Some("Missing Content-Disposition header for binary data")))))
    } yield res
  )

}
