package no.java.ems.model

import org.joda.time.DateTime
import com.mongodb.casbah.Imports._
import java.util
import util.{Date => JDate}

case class Room(id: Option[String], name: String, lastModified: DateTime = new DateTime()) extends Entity[Room] {
  type T = Room

  def withId(id: String) = copy(id = Some(id))

  def toMongo: DBObject = MongoDBObject(
    "_id" -> id.getOrElse(util.UUID.randomUUID().toString),
    "name" -> name,
    "last-modified" -> lastModified.toDate
  )
}

object Room {
  def apply(o: DBObject): Room = {
    val w = wrapDBObj(o)
    Room(
      w.getAs[String]("_id"),
      w.as[String]("name"),
      new DateTime(w.getAsOrElse[JDate]("last-modified", new JDate()))
    )
  }
}

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


case class Event(id: Option[String], name: String, slug: String, start: DateTime, end: DateTime, venue: String, rooms: Seq[Room], slots: Seq[Slot], lastModified: DateTime = new DateTime()) extends Entity[Event] {
  require(start.isBefore(end), "Start must be before End")

  type T = Event

  def withId(id: String) = copy(id = Some(id))

  def toMongo(update: Boolean): DBObject = {
    val base = MongoDBObject(
      "name" -> name,
      "slug" -> slug,
      "start" -> start.toDate,
      "end" -> end.toDate,
      "venue" -> venue,
      "last-modified" -> lastModified.toDate
    )
    if (update) {
      MongoDBObject(
        "$set" -> base
      )
    } else {
      val obj = MongoDBObject(
        "_id" -> id.getOrElse(util.UUID.randomUUID().toString),
        "rooms" -> rooms.map(_.toMongo),
        "slots" -> slots.map(_.toMongo)
      ) ++ base
      obj
    }
  }
}

object Event {
  def apply(dbo: DBObject):Event = {
    val m = wrapDBObj(dbo)
    Event(
      m.get("_id").map(_.toString),
      m.getAsOrElse("name", "No Name"),
      m.as[String]("slug"),
      new DateTime(m.getAsOrElse[JDate]("start", new JDate())),
      new DateTime(m.getAsOrElse[JDate]("end", new JDate())),
      m.getAsOrElse("venue", "Unknown"),
      m.getAsOrElse[Seq[DBObject]]("rooms", Seq()).map(Room.apply),
      m.getAsOrElse[Seq[DBObject]]("slots", Seq()).map(Slot.apply),
      new DateTime(m.getAsOrElse[JDate]("last-modified", new JDate()))
    )
  }
}