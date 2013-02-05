package no.java.ems

import model.Entity
import no.java.ems.converters._
import security.User
import storage.MongoDBStorage
import unfiltered.response._
import unfiltered.request._
import javax.servlet.http.HttpServletRequest
import net.hamnaberg.json.collection._
import no.java.unfiltered.{BaseURIBuilder, RequestURIBuilder}
import unfiltered.{IfUnmodifiedSinceString, DateResponseHeader}
import com.mongodb.MongoException
import scala.Left
import net.hamnaberg.json.collection.ErrorMessage
import unfiltered.DateResponseHeader
import scala.Right
import net.hamnaberg.json.collection.Template
import unfiltered.response.ResponseString
import net.hamnaberg.json.collection.Item
import scala.Left
import net.hamnaberg.json.collection.ErrorMessage
import unfiltered.DateResponseHeader
import scala.Right
import net.hamnaberg.json.collection.Template
import unfiltered.response.ResponseString
import net.hamnaberg.json.collection.Item
import scala.Left
import net.hamnaberg.json.collection.ErrorMessage
import unfiltered.DateResponseHeader
import scala.Right
import net.hamnaberg.json.collection.Template
import unfiltered.response.ResponseString
import net.hamnaberg.json.collection.Item
import ems.storage.BinaryStorage

trait ResourceHelper {

  def storage: MongoDBStorage

  private [ems] def handleObject[T <: Entity[T]](obj: Option[T], request: HttpRequest[HttpServletRequest], fromTemplate: (Template) => T, saveEntity: (T) => Either[Exception, T], toItem: (T) => Item)(implicit user: User) = {
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
        authenticated(req, user) {
          case IfUnmodifiedSince(date) => {
            obj.map{ old =>
              if (old.lastModified.withMillisOfSecond(0).toDate == date) {
                saveFromTemplate(req, fromTemplate, saveEntity)
              }
              else {
                PreconditionFailed
              }
            }.getOrElse(NotFound)
          }
          case IfUnmodifiedSinceString("*") => {
            saveFromTemplate(req, fromTemplate, saveEntity)
          }
          case _ => PreconditionRequired ~> ResponseString("You must include a 'If-Unmodified-Since' header in your request")
        }
      }
      case PUT(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  private def saveFromTemplate[T <: Entity[T]](req: HttpRequest[HttpServletRequest], fromTemplate: (Template) => T, saveEntity: (T) => Either[Exception, T]): ResponseFunction[Any] = {
    withTemplate(req) {
      t => {
        val e = fromTemplate(t)
        saveEntity(e).fold(
          ex => InternalServerError ~> ResponseString(ex.getMessage),
          _ => NoContent
        )
      }
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


  private [ems] def createObject[A](request: HttpRequest[HttpServletRequest],
                      fromT: (Template) => A,
                      saveObject: (A) => Either[Exception, A],
                      segments: (A) => Seq[String])
                     (implicit user: User) = {
    authenticated(request, user) {
      case req@RequestContentType(CollectionJsonResponse.contentType) & BaseURIBuilder(baseUriBuilder) => {
        withTemplate(req) {
          t => {
            val e = fromT(t)
            saveObject(e).fold(
              ex => InternalServerError ~> ResponseString(ex.getMessage),
              stored => Created ~> Location(baseUriBuilder.segments(segments(stored) : _*).build().toString)
            )
          }
        }
      }
      case _ => UnsupportedMediaType
    }
  }


  private [ems] def authenticated[B](req: HttpRequest[HttpServletRequest], user: User)(intent: unfiltered.Cycle.Intent[HttpServletRequest, B]): ResponseFunction[B] = {
    if (user.authenticated && intent.isDefinedAt(req)) intent(req) else Forbidden
  }

}
