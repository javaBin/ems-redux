package ems.model

import org.joda.time.DateTime
import com.mongodb.casbah.Imports._
import scala.Some
import java.util
import java.util.{Date => JDate}

case class Room(id: Option[String], name: String, lastModified: DateTime = new DateTime()) extends Entity[Room] {
  type T = Room

  def withId(id: String) = copy(id = Some(id))

  def toMongo: DBObject = MongoDBObject(
    "_id" -> id.getOrElse(util.UUID.randomUUID().toString),
    "name" -> name.noHtml,
    "last-modified" -> DateTime.now.toDate
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