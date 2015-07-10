package ems

import model.Entity
import security.User
import ems.storage.DBStorage
import unfiltered.response._
import unfiltered.request._
import unfilteredx._
import net.hamnaberg.json.collection._
import unfiltered.directives._
import Directives._
import javax.servlet.http.HttpServletRequest
import scala.language.implicitConversions

trait ResourceHelper extends EmsDirectives {

  def storage: DBStorage

  private [ems] def handleObject[T <: Entity[T]](obj: Option[T],
                                                 fromTemplate: (Template) => T,
                                                 saveEntity: (T) => Either[Throwable, T],
                                                 toItem: (T) => Item,
                                                 removeEntity: Option[(T) => Either[Throwable, Unit]] = None)(enrich: JsonCollection => JsonCollection = identity)(implicit user: User): ResponseDirective = {

    val resp = (i: T) => DateResponseHeader("Last-Modified", i.lastModified) ~> CollectionJsonResponse(enrich(JsonCollection(toItem(i))))

    val get = for {
      _ <- GET | HEAD
      a <- getOrElse(obj, NotFound)
      res <- ifModifiedSince(a.lastModified, resp(a))
    } yield res

    val put = for {
      _ <- PUT
      _ <- authenticated(user)
      _ <- contentType(CollectionJsonResponse.contentType)
      a <- getOrElse(obj, NotFound)
      _ <- ifUnmodifiedSince(a.lastModified)
      res <- saveFromTemplate(fromTemplate, saveEntity)
    } yield res

    val delete = for {
      _ <- DELETE
      _ <- authenticated(user)
      a <- getOrElse(obj, NotFound)
      _ <- ifUnmodifiedSince(a.lastModified)
      f <- getOrElse(removeEntity, Forbidden)
      _ <- fromEither(f(a))
    } yield NoContent

    get | put | delete
  }

  private def saveFromTemplate[T <: Entity[T]](fromTemplate: (Template) => T, saveEntity: (T) => Either[Throwable, T]): ResponseDirective = {
    for {
      parsed <- withTemplate(fromTemplate)
      extract <- parsed
      e <- saveEntity(extract)
    } yield NoContent
  }

  private [ems] def withTemplate[T](fromTemplate: (Template) => T): Directive[HttpServletRequest, ResponseFunction[Any], Either[Throwable, T]] = {
    for {
      template <- inputStream.map(is => NativeJsonCollectionParser.parseTemplate(is))
    } yield template.right.map(fromTemplate)
  }

  implicit def fromEither[T](either: Either[Throwable, T]): Directive[HttpServletRequest, ResponseFunction[Any], T] = {
    either.fold(
      ex => failure(InternalServerError ~> ResponseString(ex.getMessage)),
      a => success(a)
    )
  }


  private [ems] def createObject[A <: Entity[A]](fromTemplate: (Template) => A,
                                                 saveObject: (A) => Either[Throwable, A],
                                                 segments: (A) => Seq[String],
                                                 links: (A) => Seq[LinkHeader])
                                                (implicit user: User): ResponseDirective = {
    for {
      _ <- POST
      _ <- authenticated(user)
      _ <- contentType(CollectionJsonResponse.contentType)
      rb <- baseURIBuilder
      parsed <- withTemplate(fromTemplate)
      extract <- parsed
      e <- saveObject(extract)
    } yield {
      val l = links(e)
      Created ~> Location(rb.segments(segments(e) : _*).build().toString) ~> new Responder[Any] {
        def respond(res: HttpResponse[Any]) {
          l.foreach(_.respond(res))
        }
      }
    }
  }

  private [ems] def authenticated(user: User) = {
    commit(getOrElse(Some(user).filter(_.authenticated), Forbidden))
  }

  protected def makeTemplate(names: String*): Template = {
    val props = names.map(n => ValueProperty(n)).toList
    Template(props)
  }
}
