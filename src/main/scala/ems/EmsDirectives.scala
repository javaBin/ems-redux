package ems

import org.joda.time.{DateTimeZone, DateTime}
import unfiltered.request._
import unfiltered.response._
import unfilteredx._
import net.hamnaberg.json.collection.{Error, JsonCollection}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.std.scalaFuture._
trait EmsDirectives {
  val Directives = d2.Directives[Future]
  import Directives._

  type ResponseDirective = Directive[Any, ResponseFunction[Any], ResponseFunction[Any]]

  def baseURIBuilder = request.map(r => BaseURIBuilder(r))

  def baseURI = request.map(r => BaseURI(r))

  def requestURIBuilder = request.map(r => RequestURIBuilder(r))

  def requestURI = request.map(r => RequestURI(r))

  def ifModifiedSince[A](dt: DateTime, res: ResponseFunction[Any]) = request.flatMap{
    case IfModifiedSinceString("*") => error(NotModified)
    case IfModifiedSince(date) if dt.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toDate == date => error(NotModified)
    case _ => success(res)
  }

  def ifUnmodifiedSince(dt: DateTime) = request.flatMap{
    case IfUnmodifiedSinceString("*") => success(())
    case IfUnmodifiedSince(date) if dt.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toDate == date => success(())
    case IfUnmodifiedSince(date) => error(PreconditionFailed ~> ResponseString(s"${dt.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toDate} is not equal to $date"))
    case RequestURI(href) => error(PreconditionRequired ~> CollectionJsonResponse(JsonCollection(href, Error("Wrong response", None, Some("Missing If-Unmodified-Since header")))))
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

  def inputStream = request.map(_.inputStream)

  def queryParams = request.map{case QueryParams(qp) => qp}
}
