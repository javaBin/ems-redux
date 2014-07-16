package ems

import javax.servlet.http.HttpServletRequest
import org.joda.time.{DateTimeZone, DateTime}
import unfiltered.directives._
import unfiltered.directives.Result._
import Directives._
import unfiltered.request._
import unfiltered.response._
import unfilteredx._
import net.hamnaberg.json.collection.{Error, JsonCollection}

trait EmsDirectives {
  type ResponseDirective = Directive[HttpServletRequest, ResponseFunction[Any], ResponseFunction[Any]]

  def baseURIBuilder = request[HttpServletRequest].map(r => BaseURIBuilder(r))

  def baseURI = request[HttpServletRequest].map(r => BaseURI(r))

  def requestURIBuilder = request[Any].map(r => RequestURIBuilder(r))

  def requestURI = request[Any].map(r => RequestURI(r))

  def ifModifiedSince(dt: DateTime, res: ResponseFunction[Any]) = Directive[Any, ResponseFunction[Any], ResponseFunction[Any]]{
    case IfModifiedSinceString("*") => Result.Error(NotModified)
    case IfModifiedSince(date) if dt.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toDate == date => Result.Error(NotModified)
    case _ => Success(res)
  }

  def ifUnmodifiedSince(dt: DateTime) = Directive[Any, ResponseFunction[Any], Unit]{
    case IfUnmodifiedSinceString("*") => Success(())
    case IfUnmodifiedSince(date) if dt.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toDate == date => Success(())
    case IfUnmodifiedSince(date) => Result.Error(PreconditionFailed ~> ResponseString(s"${dt.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toDate} is not equal to $date"))
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
