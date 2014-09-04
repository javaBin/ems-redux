package ems

import org.joda.time.DateTime
import scala.language.implicitConversions

package object storage {
  implicit def toDateTime(dt: java.util.Date) = new DateTime(dt)
}
