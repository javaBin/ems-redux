package ems.graphql

import ems.graphql.DataTypes.{DateTimeType, UUIDType}
import ems.model.{Event, Session, Speaker}
import sangria.schema._

object EmsSchema {

  private val SpeakerType = ObjectType(
    "Speaker",
    fields[Unit, Speaker](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("name", StringType, resolve = _.value.name),
      Field("bio", OptionType(StringType), resolve = _.value.bio)
    ))

  private val SessionType = ObjectType(
    "Session",
    fields[GraphQlService, Session](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("title", StringType, resolve = _.value.abs.title),
      Field("body", OptionType(StringType), resolve = _.value.abs.body),
      Field("summary", OptionType(StringType), resolve = _.value.abs.summary),
      Field("audience", OptionType(StringType), resolve = _.value.abs.audience),
      Field("speakers", ListType(SpeakerType), resolve = args => args.ctx.getSpeakers(args.value.id.get)),
      Field("level", StringType, resolve = _.value.abs.level.name),
      Field("format", StringType, resolve = _.value.abs.format.name),
      Field("language", StringType, resolve = _.value.abs.language.getLanguage),
      Field("lastModified", DateTimeType, resolve = _.value.lastModified)
    ))

  private val EventType = ObjectType(
    "Event",
    fields[GraphQlService, Event](
      Field("id", UUIDType, resolve = _.value.id.get),
      Field("name", StringType, resolve = _.value.name),
      Field("venue", StringType, resolve = _.value.venue),
      Field("lastModified", DateTimeType, resolve = _.value.lastModified),
      Field("sessions", ListType(SessionType), resolve = args => args.ctx.getSessions(args.value.id.get))
    ))

  private val EventIdArgument: Argument[Option[Seq[String]]] = Argument(
    name = "id",
    argumentType = OptionInputType(ListInputType(IDType)),
    description = "The event ids"
  )

  val EmsSchema = Schema(
    query = ObjectType(
      name = "Query",
      description = "Ems GraphQl Query",
      fields[GraphQlService, Unit](
        Field(
          "events",
          ListType(EventType),
          arguments = EventIdArgument :: Nil,
          resolve = args => args.ctx.getEvents(args.argOpt[Seq[String]]("id"))
        )
      )
    )
  )

}