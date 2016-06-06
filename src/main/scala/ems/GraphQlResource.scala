package ems

import ems.storage.DBStorage
import org.json4s.native.JsonMethods._
import sangria.execution.Executor
import sangria.marshalling.json4s.native.Json4sNativeResultMarshaller.Node
import sangria.parser.QueryParser
import sangria.schema.Schema
import unfiltered.request._
import unfiltered.response._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source
import scala.util.{Failure, Success}

trait GraphQlResource extends ResourceHelper {

  def storage: DBStorage

  def emsSchema: Schema[Unit, Unit]

  def ec: ExecutionContext

  import Directives._
  import ops._
  import sangria.marshalling.json4s.native._

  def handleGraphQl(): ResponseDirective = {
    for {
      _ <- POST
      queryString <- request.map(r => Source.fromInputStream(r.inputStream).mkString)
      qRes = parseQuery(queryString)
      _ <- commit
      res <- getOrElse(qRes, InternalServerError)
    } yield  JsonContent ~> ResponseString(compact(render(res)))
  }

  private def parseQuery(queryString: String): Option[Node] = {
    implicit val e: ExecutionContext = ec
    QueryParser.parse(queryString) match {
      case Success(qDsl) => Some(Await.result(Executor.execute(emsSchema, qDsl), 10 seconds))
      case Failure(t) => None
    }
  }

}
