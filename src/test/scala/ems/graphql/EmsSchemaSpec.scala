package ems.graphql

import org.json4s.native.JsonMethods._
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import sangria.execution.{Executor, ValidationError}
import sangria.marshalling.json4s.native.Json4sNativeResultMarshaller.Node
import sangria.parser.QueryParser

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Success, Try}

class EmsSchemaSpec extends Specification with JsonMatchers {

  import sangria.marshalling.json4s.native._

  import scala.concurrent.ExecutionContext.Implicits.global

  "Schema" should {

    "find all events" in {
      val node: Node = executeQuery(
        """
          | {
          |   event {
          |     name
          |   }
          | }
        """.stripMargin)

      pretty(render(node)) must /("data") / ("event") */ ("name" -> "Event 1")
      pretty(render(node)) must /("data") / ("event") */ ("name" -> "Event 2")
    }

    "find one event by id" in {
      val node: Node = executeQuery(
        """
          | {
          |   event(id : "d7af21bd-e040-4e1f-9b45-71918b5e46cd") {
          |     name
          |   }
          | }
        """.stripMargin)

      pretty(render(node)) must /("data") / ("event") */ ("name" -> "Event 1")
      pretty(render(node)) must not / ("data") / ("event") */ ("name" -> "Event 2")
    }

    "fail on none existing node " in {
      val node = Try(executeQuery(
        """
          | {
          |   does_not_exists {
          |     name
          |   }
          | }
        """.stripMargin))

      node must beFailedTry.withThrowable[ValidationError]
    }
  }

  def executeQuery(query: String): Node = {
    val schema: EmsSchema = new EmsSchema(new DummyDbStorage)
    QueryParser.parse(
      query) match {
      case Success(dsl) =>
        Await.result(
          Executor.execute(
            schema.schema,
            dsl
          ),
          10 seconds)
    }
  }


}
