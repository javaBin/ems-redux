package no.java.ems

import org.joda.time.DateTime

package object storage {
  implicit def toDateTime(dt: java.util.Date) = new DateTime(dt)
}