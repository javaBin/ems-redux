package ems.storage

import java.sql.Timestamp

import doobie.imports._
import ems.model.Event
import org.joda.time.DateTime
import org.postgresql.util.PGobject
import scalaz.concurrent.Task
import argonaut._, Argonaut._
import scala.reflect.runtime.universe.TypeTag
import scalaz.syntax.id._

case class InternalEvent(id: String, name: String, slug:String, venue: String, lastModified: DateTime) {
  def toEvent = Event(Some(id), name, slug, venue, Nil, lastModified)
}



class SQLStorage(xa: Transactor[Task]) {

  implicit val JsonMeta: Meta[Json] = 
  Meta.other[PGobject]("json").nxmap[Json](
    a => Parse.parse(a.getValue).leftMap[Json](sys.error).merge, // failure raises an exception
    a => new PGobject <| (_.setType("json")) <| (_.setValue(a.nospaces))
  )

  def codecMeta[A >: Null : CodecJson: TypeTag]: Meta[A] =
  Meta[Json].nxmap[A](
    _.as[A].result.fold(p => sys.error(p._1), identity), 
    _.asJson
  )

  implicit val jodaTime: Meta[DateTime] = Meta[Timestamp].xmap(d => new DateTime(d), t => new Timestamp(t.getMillis))

  def getEvents(): Vector[Event] = {
    val query = sql"select * from event".query[InternalEvent].process
    xa.transP(query).map(_.toEvent).vector.run
  }

  def getSessions(eventId: String) = {
    val query = sql"select * from event_session".query[InternalEvent].process
  }
}
