package ems
package model

import org.joda.time.DateTime

trait Entity[T] {
  def id: Option[UUID]

  def lastModified: DateTime

  def withId(id: UUID = randomUUID): T
}
