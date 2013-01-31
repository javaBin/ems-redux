package no.java.ems.model

import org.joda.time.DateTime

trait Entity[T] {
  def id: Option[String]

  def lastModified: DateTime

  def withId(id: String): T
}
