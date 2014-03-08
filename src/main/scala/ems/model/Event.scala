package ems.model

import org.joda.time.DateTime
import com.mongodb.casbah.Imports._
import java.util
import util.{Date => JDate}

case class Event(id: Option[String], name: String, slug: String, venue: String, rooms: Seq[Room], lastModified: DateTime = new DateTime()) extends Entity[Event] {
  type T = Event

  def withId(id: String) = copy(id = Some(id))

  def toMongo(update: Boolean): DBObject = {
    val base = MongoDBObject(
      "name" -> name.noHtml,
      "slug" -> slug,
      "venue" -> venue.noHtml,
      "last-modified" -> DateTime.now.toDate
    )
    if (update) {
      MongoDBObject(
        "$set" -> base
      )
    } else {
      val obj = MongoDBObject(
        "_id" -> id.getOrElse(util.UUID.randomUUID().toString),
        "rooms" -> rooms.map(_.toMongo)
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
      m.getAsOrElse("venue", "Unknown"),
      m.getAsOrElse[Seq[DBObject]]("rooms", Seq()).map(Room.apply),
      new DateTime(m.getAsOrElse[JDate]("last-modified", new JDate()))
    )
  }
}

case class EventWithSessionCount(event: Event, count : Int = -1)