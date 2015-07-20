package ems
package model

import org.joda.time.DateTime

case class Event(id: Option[UUID], name: String, slug: String, venue: String, lastModified: DateTime = new DateTime()) extends Entity[Event] {
  type T = Event

  def withId(id: UUID) = copy(id = Some(id))
}

case class EventWithSessionCount(event: Event, count : Int = -1)
