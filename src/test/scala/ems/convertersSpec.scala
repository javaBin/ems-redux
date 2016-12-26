package ems

import java.util.UUID

import ems.model.Slot
import net.hamnaberg.json.collection.{Template, ValueProperty}
import org.specs2.mutable.Specification

class convertersSpec extends Specification {

  "converters" should {
    "map to slots" in {
      val emsId: UUID = ems.UUIDFromString(UUID.randomUUID().toString)
      val date = "2017-01-02T12:15:00Z"
      val slot: Slot = converters.toSlot(
        Template(
          ValueProperty("start", Some(date)),
          ValueProperty("duration", Some("45"))
        ),
        emsId
      )
      slot.duration.getStandardMinutes must beEqualTo(45L)
    }
  }
}
