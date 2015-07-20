import language.implicitConversions
import java.util.UUID

import scala.util.Try

package object ems {
  type UUID = java.util.UUID

  def randomUUID: UUID = UUID.randomUUID()
  implicit def UUIDFromString(s: String): UUID = UUID.fromString(s)
  def UUIDFromStringOpt(s: String): Option[UUID] = Try{ UUID.fromString(s) }.toOption

  implicit def UUIDToString(uuid: UUID): String = uuid.toString
}
