package ems

import ems.graphql.{EmsSchema, GraphQlService}
import org.json4s.native.JsonMethods._
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.json4s.native.Json4sNativeResultMarshaller.Node
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import unfiltered.request._
import unfiltered.response._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

trait GraphQlResource extends EmsDirectives {

  import Directives._
  import ops._
  import sangria.marshalling.json4s.native._

  def graphQlService: GraphQlService
  def ec: ExecutionContext

  def handleGraphQl: ResponseDirective = {
    for {
      _ <- GET
      queryParam <- queryParam("query")
      queryBody <- bodyContent
      query <- getOrElse(queryParam.orElse(queryBody), BadRequest ~> ResponseString(s"Missing query!"))
      result <- parseQuery(query)
    } yield creaseResponse(Ok, result)
  }

  def queryParam(name: String) = {
    queryParams.map(_.get(name).flatMap(_.headOption))
  }

  def bodyContent = {
    request.map(r => Source.fromInputStream(r.inputStream).mkString.trim)
        .map(s => if (s.nonEmpty) Some(s) else None)
  }

  def handleGraphQlSchema: ResponseDirective = {
    for {
      _ <- GET
    } yield Ok ~> ResponseString(SchemaRenderer.renderSchema(EmsSchema.schema))
  }

  private def creaseResponse(status: Status, qae: Node): ResponseFunction[Any] = {
    status ~> JsonContent ~> ResponseString(compact(render(qae)))
  }

  private def parseQuery(queryString: String) = {
    implicit val e: ExecutionContext = ec
    def handleException(ex: Throwable) = {
      ex match {
        case qae: QueryAnalysisError => failure(creaseResponse(BadRequest, qae.resolveError))
        case ewr: ErrorWithResolver => failure(creaseResponse(InternalServerError, ewr.resolveError))
        case unhandled => failure(InternalServerError ~> ResponseString(
            s"Unable to execute query: ${unhandled.getClass.getSimpleName} :: ${unhandled.getMessage}"))
      }
    }

    QueryParser.parse(queryString) match {
      case Success(qDsl) => {
        Try(Await.result(Executor.execute(EmsSchema.schema, qDsl, graphQlService), 10 seconds)) match {
          case Success(node) => success(node)
          case Failure(ex) => handleException(ex)
        }
      }
      case Failure(t) => handleException(t)
    }
  }

}
