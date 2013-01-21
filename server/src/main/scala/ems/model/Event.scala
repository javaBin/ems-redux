package no.java.ems.model

import org.joda.time.DateTime

case class Room(id: Option[String], name: String, lastModified: DateTime = new DateTime()) extends Entity {
  type T = Room

  def withId(id: String) = copy(id = Some(id))
}

case class Slot(id: Option[String], start: DateTime, end: DateTime, lastModified: DateTime = new DateTime()) extends Entity {
  type T = Slot

  def withId(id: String) = copy(id = Some(id))
}

case class Event(id: Option[String], name: String, slug: String, start: DateTime, end: DateTime, venue: String, rooms: Seq[Room], slots: Seq[Slot], lastModified: DateTime = new DateTime()) extends Entity {
  require(start.isBefore(end), "Start must be before End")

  type T = Event

  def withId(id: String) = copy(id = Some(id))
}