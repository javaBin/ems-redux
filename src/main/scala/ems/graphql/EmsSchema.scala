package ems.graphql

import java.util.UUID

import ems.graphql.DataTypes.UUIDType
import ems.model.Event
import ems.storage.DBStorage
import sangria.schema._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class EmsSchema(store: DBStorage)(implicit executionContext: ExecutionContext) {
  private val eventType = ObjectType(
    "Event",
    fields[Unit, Event](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("name", StringType, resolve = _.value.name),
      Field("venue", StringType, resolve = _.value.venue)
    ))

  private val argEventId: Argument[Option[Seq[String]]] = Argument(
    name = "id",
    argumentType = OptionInputType(ListInputType(IDType)),
    description = "The event ids"
  )

  val schema = Schema(
    ObjectType(
      "Query",
      fields[Unit, Unit](
        Field(
          "event",
          ListType(eventType),
          arguments = argEventId :: Nil,
          resolve = args => getEvents(args.argOpt[Seq[String]]("id"))
        )
      )
    )
  )

  def getEvents(ids: Option[Seq[String]]): List[Event] = {
    val value: Future[List[Event]] = ids.getOrElse(List()) match {
      case Nil => store.getEvents().map(_.toList)
      case idsAsString => {
        val queryIds: Seq[UUID] = idsAsString.map(UUID.fromString)
        Future.sequence(queryIds.map(store.getEvent).toList)
            .map(optEvent => optEvent.flatMap(_.toList))
      }
    }
    Await.result(value, 10 seconds)
  }

}