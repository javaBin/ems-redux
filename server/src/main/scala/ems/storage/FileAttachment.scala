package no.java.ems.storage

import java.io.File
import no.java.ems.{StreamingAttachment, Attachment}
import no.java.ems.model.Entity

case class FileAttachment(id: Option[String], file: File) extends Entity[Attachment] with Attachment {
  lazy val underlying = StreamingAttachment(file)

  type T = FileAttachment

  def lastModified = underlying.lastModified

  def withId(id: String) = copy(id = Some(id))

  def name = underlying.name

  def size = Some(file.length())

  def mediaType = underlying.mediaType
}
