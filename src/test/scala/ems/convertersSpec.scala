package ems

import java.util.UUID

import ems.model.Slot
import net.hamnaberg.json.collection.{Template, ValueProperty}
import org.specs2.mutable.Specification

class convertersSpec extends Specification {
  val eventId: UUID = ems.UUIDFromString(UUID.randomUUID().toString)

  "converters" should {
    "map to slots" in {
      val date = "2017-01-02T12:15:00Z"
      val slot: Slot = converters.toSlot(
        Template(
          ValueProperty("start", Some(date)),
          ValueProperty("duration", Some("45"))
        ),
        eventId
      )
      slot.duration.getStandardMinutes must beEqualTo(45L)
    }

    "map to room" in {
      val roomName = "The room name"
      val room = converters.toRoom(
        Template(
          ValueProperty("name", Some(roomName))
        ),
        eventId
      )
      room.name must beEqualTo(roomName)
    }
  }
}
