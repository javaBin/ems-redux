package ems
package storage

import ems.model.Entity
import java.io.InputStream

trait BinaryStorage {
  def getAttachment(id: UUID): Option[Attachment with Entity[Attachment]]

  def saveAttachment(att: Attachment): Attachment with Entity[Attachment]

  def removeAttachment(id: UUID)

  def getStream(att: Attachment): InputStream = att match {
    case u: StreamingAttachment => u.data
    case u: FileAttachment => u.data
    case _ => throw new IllegalArgumentException("No stream available for %s".format(att.getClass.getName))
  }

}
