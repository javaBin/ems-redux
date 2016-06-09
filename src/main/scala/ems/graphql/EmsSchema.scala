package ems.graphql

import java.util.UUID

import ems.UUID
import ems.graphql.DataTypes.{DateTimeType, UUIDType}
import ems.model.{Event, Session, Speaker}
import ems.security.Anonymous
import ems.storage.DBStorage
import sangria.schema._

import scala.concurrent.{ExecutionContext, Future}

class EmsSchema(store: DBStorage)(implicit executionContext: ExecutionContext) {

  private val speakerType = ObjectType(
    "Speaker",
    fields[Unit, Speaker](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("name", StringType, resolve = _.value.name),
      Field("bio", OptionType(StringType), resolve = _.value.bio)
    ))

  private val sessionType = ObjectType(
    "Session",
    fields[Unit, Session](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("title", StringType, resolve = _.value.abs.title),
      Field("body", OptionType(StringType), resolve = _.value.abs.body),
      Field("summary", OptionType(StringType), resolve = _.value.abs.summary),
      Field("audience", OptionType(StringType), resolve = _.value.abs.audience),
      Field("speakers", ListType(speakerType), resolve = args => getSpeakers(args.value.id.get)),
      Field("level", StringType, resolve = _.value.abs.level.name),
      Field("format", StringType, resolve = _.value.abs.format.name),
      Field("language", StringType, resolve = _.value.abs.language.getLanguage),
      Field("lastModified", DateTimeType, resolve = _.value.lastModified)
    ))

  private val eventType = ObjectType(
    "Event",
    fields[Unit, Event](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("name", StringType, resolve = _.value.name),
      Field("venue", StringType, resolve = _.value.venue),
      Field("lastModified", DateTimeType, resolve = _.value.lastModified),
      Field("sessions", ListType(sessionType), resolve = args => getSessions(args.value.id.get))
    ))

  private val argEventId: Argument[Option[Seq[String]]] = Argument(
    name = "id",
    argumentType = OptionInputType(ListInputType(IDType)),
    description = "The event ids"
  )

  val schema = Schema(
    query = ObjectType(
      name = "Query",
      description = "Ems GraphQl Query",
      fields[Unit, Unit](
        Field(
          "events",
          ListType(eventType),
          arguments = argEventId :: Nil,
          resolve = args => getEvents(args.argOpt[Seq[String]]("id"))
        )
      )
    )
  )

  def getSpeakers(sessionId: UUID): Future[List[Speaker]] = {
    store.getSpeakers(sessionId).map(_.toList)
  }

  def getSessions(eventId: UUID): Future[List[Session]] = {
    store.getSessions(eventId)(Anonymous).map(_.toList)
  }

  def getEvents(ids: Option[Seq[String]]): Future[List[Event]] = {
    ids.getOrElse(List()) match {
      case Nil => store.getEvents().map(_.toList)
      case idsAsString => {
        val queryIds: Seq[UUID] = idsAsString.map(UUID.fromString)
        Future.sequence(queryIds.map(store.getEvent).toList)
            .map(optEvent => optEvent.flatMap(_.toList))
      }
    }
  }

}