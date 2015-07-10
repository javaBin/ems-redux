package ems.model

import org.joda.time.DateTime

case class Room(id: Option[String], name: String, lastModified: DateTime = new DateTime()) extends Entity[Room] {
  type T = Room

  def withId(id: String) = copy(id = Some(id))
}
