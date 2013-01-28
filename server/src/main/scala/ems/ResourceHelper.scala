package no.java.ems

import model.Entity
import storage.MongoDBStorage
import unfiltered.response._
import unfiltered.request._
import javax.servlet.http.HttpServletRequest
import net.hamnaberg.json.collection._
import no.java.unfiltered.RequestURIBuilder
import unfiltered.{IfUnmodifiedSinceString, DateResponseHeader}

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait ResourceHelper {

  def storage: MongoDBStorage

  private [ems] def handleObject[T <: Entity](obj: Option[T], request: HttpRequest[HttpServletRequest], fromTemplate: (Template) => T, toItem: (T) => Item) = {
    request match {
      case GET(req) => {
        req match {
          case IfModifiedSince(date) if (obj.isDefined && obj.get.lastModified.withMillisOfSecond(0).toDate == date) => {
            NotModified
          }
          case _ => {
             obj.map { i =>
               DateResponseHeader("Last-Modified", i.lastModified.getMillis) ~> CollectionJsonResponse(JsonCollection(toItem(i)))
             }.getOrElse(NotFound)
          }
        }
      }
      case req@PUT(RequestContentType(CollectionJsonResponse.contentType)) => {
        req match {
          case IfUnmodifiedSince(date) => {
            obj.map{ old =>
              if (old.lastModified.withMillisOfSecond(0).toDate == date) {
                withTemplate(req) {
                  t => {
                    val e = fromTemplate(t)
                    storage.saveEntity(e)
                    NoContent
                  }
                }
              }
              else {
                PreconditionFailed
              }
            }.getOrElse(NotFound)
          }
          case IfUnmodifiedSinceString("*") => {
            withTemplate(req) {
              t => {
                val e = fromTemplate(t)
                storage.saveEntity(e)
                NoContent
              }
            }
          }
          case _ => PreconditionRequired ~> ResponseString("You must include a 'If-Unmodified-Since' header in your request")
        }
      }
      case PUT(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }
  private [ems] def withTemplate[A](req: HttpRequest[HttpServletRequest])(f: (Template) => ResponseFunction[A]) = {
    val requestUriBuilder = RequestURIBuilder.unapply(req).get
    req match {
      case RequestContentType(CollectionJsonResponse.contentType) => {
        val template = LiftJsonCollectionParser.parseTemplate(req.inputStream)
        template match {
          case Left(e) => {
            e.printStackTrace()
            BadRequest ~>
              CollectionJsonResponse(
                JsonCollection(
                  requestUriBuilder.build(),
                  ErrorMessage("Error with request", None, Option(e.getMessage))
                )
              )
          }
          case Right(t) => {
            f(t)
          }
        }
      }
      case _ => UnsupportedMediaType
    }
  }

}
