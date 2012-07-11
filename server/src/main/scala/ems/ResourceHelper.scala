package no.java.ems

import model.Entity
import unfiltered.response._
import unfiltered.request._
import javax.servlet.http.HttpServletRequest
import net.hamnaberg.json.collection._
import no.java.unfiltered.RequestURIBuilder

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait ResourceHelper { this: Storage =>

  private [ems] def handleObject[T <: Entity](obj: Option[T], request: HttpRequest[HttpServletRequest], fromTemplate: (Template) => T, toItem: (T) => Item) = {
    request match {
      case GET(req) => {
        req match {
          case IfModifiedSince(date) if (obj.isDefined && obj.get.lastModified.toDate == date) => {
            NotModified
          }
          case _ => {
            obj.map(toItem).map(JsonCollection(_)).map(CollectionJsonResponse(_)).getOrElse(NotFound)
          }
        }
      }
      case req@PUT(RequestContentType(CollectionJsonResponse.contentType)) => {
        req match {
          case IfUnmodifiedSince(date) if (obj.isDefined && obj.get.lastModified.toDate == date) => {
            withTemplate(req) {
              t => {
                val e = fromTemplate(t)
                this.saveEntity(e)
                NoContent
              }
            }
          }
          case _ => PreconditionFailed
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
          case Left(e) => BadRequest ~>
            CollectionJsonResponse(
              JsonCollection(
                requestUriBuilder.build(),
                ErrorMessage("Error with request", None, Option(e.getMessage))
              )
            )
          case Right(t) => {
            f(t)
          }
        }
      }
      case _ => UnsupportedMediaType
    }
  }

}
