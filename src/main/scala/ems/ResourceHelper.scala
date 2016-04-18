package ems

import model.Entity
import security.User
import ems.storage.DBStorage
import unfiltered.response._
import unfiltered.request._
import unfilteredx._
import net.hamnaberg.json.collection._
import scala.concurrent.Future
import scala.language.implicitConversions
import scalaz.\/
import java.net.URI
import java.sql.SQLException

trait ResourceHelper extends EmsDirectives {

  def storage: DBStorage
  import Directives._
  import ops._

  private [ems] def handleObject[T <: Entity[T]](obj: Future[Option[T]],
                                                 fromTemplate: (Template) => T,
                                                 saveEntity: (T) => Future[T],
                                                 toItem: (T) => Item,
                                                 removeEntity: Option[(T) => Future[Unit]] = None)(enrich: JsonCollection => JsonCollection = identity)(implicit user: User): ResponseDirective = {

    val resp = (i: T) => DateResponseHeader("Last-Modified", i.lastModified) ~> CollectionJsonResponse(enrich(JsonCollection(toItem(i))))

    val get = for {
      _ <- GET
      a <- getOrElseF(obj, NotFound)
      _ <- commit
      res <- ifModifiedSince(a.lastModified, resp(a))
    } yield res

    val put = for {
      _ <- PUT
      _ <- authenticated(user)
      _ <- contentType(CollectionJsonResponse.contentType)
      a <- getOrElseF(obj, NotFound)
      _ <- commit
      _ <- ifUnmodifiedSince(a.lastModified)
      res <- saveFromTemplate(fromTemplate, saveEntity)
    } yield res

    val delete = for {
      _ <- DELETE
      _ <- authenticated(user)
      a <- getOrElseF(obj, NotFound)
      _ <- commit
      _ <- ifUnmodifiedSince(a.lastModified)
      f <- getOrElse(removeEntity, Forbidden)
      _ <- f(a).successValue
    } yield NoContent

    get | put | delete
  }

  private def saveFromTemplate[T <: Entity[T]](fromTemplate: (Template) => T, saveEntity: (T) => Future[T]): ResponseDirective = {
    for {
      parsed <- withTemplate(fromTemplate)
      extract <- fromEither(parsed)
      e <- saveEntity(extract).successValue
    } yield NoContent
  }

  private [ems] def withTemplate[T](fromTemplate: (Template) => T): Directive[Any, ResponseFunction[Any], Throwable \/ T] = {
    for {
      req <- request
      template <- success(\/.fromEither(NativeJsonCollectionParser.parseTemplate(req.inputStream)))
    } yield {
      template.map(fromTemplate)
    }
  }

  def fromEither[T](either: Throwable \/ T): Directive[Any, ResponseFunction[Any], T] = {
    for {
      href <- requestURI
      res <- either.fold(
        ex => error(handleThrowable(href, ex)),
        a => success(a)
      )
    } yield {
      res
    }
  }

  private def handleThrowable(href: URI, throwable: Throwable): ResponseFunction[Any] = {
    throwable match {
      case ex: SQLException if (ex.getErrorCode == 23505) => {
        Conflict ~> CollectionJsonResponse(JsonCollection(href, Error(ex.getMessage, None, None)))
      }
      case ex => InternalServerError ~> ResponseString(ex.getMessage)
    }
  }

  private [ems] def createObject[A <: Entity[A]](fromTemplate: (Template) => A,
                                                 saveObject: (A) => Future[A],
                                                 segments: (A) => Seq[String],
                                                 links: (A) => Seq[LinkHeader])
                                                (implicit user: User): ResponseDirective  = {
    for {
      _ <- POST
      _ <- authenticated(user)
      _ <- contentType(CollectionJsonResponse.contentType)
      rb <- baseURIBuilder
      parsed <- withTemplate(fromTemplate)
      extract <- fromEither(parsed)
      e <- saveObject(extract).successValue
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
