package ems
package model

import org.joda.time.DateTime

case class Room(id: Option[UUID], eventId: UUID, name: String, lastModified: DateTime = new DateTime()) extends Entity[Room] {
  type T = Room

  def withId(id: UUID) = copy(id = Some(id))
}
