package ems.storage

import java.io.{FileInputStream, File}
import ems.{MIMEType, StreamingAttachment, Attachment}
import ems.model.Entity
import org.joda.time.DateTime

case class FileAttachment(id: Option[String], file: File, name: String, mediaType: Option[MIMEType]) extends Entity[Attachment] with Attachment {

  def lastModified = new DateTime(file.lastModified)

  def withId(id: String) = copy(id = Some(id))

  def size = Some(file.length())

  def data = new FileInputStream(file)
}