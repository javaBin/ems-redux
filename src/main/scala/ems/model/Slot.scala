package ems.model

import org.joda.time.{Minutes, Duration, DateTime}
import com.mongodb.casbah.Imports._
import scala.Some
import java.util
import java.util.{Date => JDate}

case class Slot(id: Option[String], eventId: String, start: DateTime, duration: Duration, parentId: Option[String] = None, lastModified: DateTime = DateTime.now()) extends Entity[Slot] {
  type T = Slot

  def withId(id: String) = copy(id = Some(id))

  def toMongo: DBObject = MongoDBObject(
    "_id" -> id.getOrElse(util.UUID.randomUUID().toString),
    "eventId" -> eventId,
    "start" -> start.toDate,
    "duration" -> duration.getStandardMinutes.toInt,
    "parentId" -> parentId,
    "last-modified" -> DateTime.now.toDate
  )
}

object Slot {
  def apply(o: DBObject): Slot = {
    val w = wrapDBObj(o)
    Slot(
      w.getAs[String]("_id"),
      w.get("eventId").toString,
      new DateTime(w.getAsOrElse[JDate]("start", new JDate(0L))),
      Minutes.minutes(w.getAsOrElse[Double]("duration", 0D).toInt).toStandardDuration,
      w.getAs[String]("parentId"),
      new DateTime(w.getAsOrElse[JDate]("last-modified", new JDate()))
    )
  }
}

case class SlotTree(parent: Slot, children: Seq[Slot])