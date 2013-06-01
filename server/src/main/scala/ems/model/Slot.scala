package ems.model

import org.joda.time.DateTime
import com.mongodb.casbah.Imports._
import scala.Some
import java.util
import java.util.{Date => JDate}

case class Slot(id: Option[String], start: DateTime, end: DateTime, lastModified: DateTime = new DateTime()) extends Entity[Slot] {
  type T = Slot

  def withId(id: String) = copy(id = Some(id))

  def toMongo: DBObject = MongoDBObject(
    "_id" -> id.getOrElse(util.UUID.randomUUID().toString),
    "start" -> start.toDate,
    "end" -> end.toDate,
    "last-modified" -> lastModified.toDate
  )
}

object Slot {
  def apply(o: DBObject): Slot = {
    val w = wrapDBObj(o)
    Slot(
      w.getAs[String]("_id"),
      new DateTime(w.getAsOrElse[JDate]("start", new JDate(0L))),
      new DateTime(w.getAsOrElse[JDate]("end", new JDate(0L))),
      new DateTime(w.getAsOrElse[JDate]("last-modified", new JDate()))
    )
  }
}
