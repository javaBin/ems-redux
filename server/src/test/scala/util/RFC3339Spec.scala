package util

import org.specs2.mutable.Specification
import org.joda.time.{DateMidnight, DateTimeZone, DateTime}

class RFC3339Spec extends Specification {
  "RFC-3339" should {
    "parse full-date correctly" in {
      val input = "1985-04-12"
      RFC3339.parseDateTime(input) must beRight(new DateTime(1985, 4, 12, 0, 0, 0, 0, DateTimeZone.UTC))
    }

    "parse full-datetime correctly" in {
      val input = "1985-04-12T23:20:50.52Z"
      RFC3339.parseDateTime(input) must beRight(new DateTime(1985, 4, 12, 23, 20, 50, 520, DateTimeZone.UTC))
    }

    "parse the end of 1990" in {
      val input = "1990-12-31T15:59:59-08:00"
      RFC3339.parseDateTime(input) must beRight[DateTime]
    }

    "generated must be the same as parsed" in {
      val dt = new DateMidnight(1970, 1, 1, DateTimeZone.UTC).toDateTime
      val expected = "1970-01-01T00:00:00Z"
      val generated = RFC3339.format(dt)
      expected must beEqualTo(generated)
      RFC3339.parseDateTime(generated) must beRight(dt)
    }
  }
}
