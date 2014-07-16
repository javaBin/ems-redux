package ems.storage

import com.mongodb.casbah.gridfs.GenericGridFSDBFile
import ems.{MIMEType, Attachment}
import ems.model.Entity
import java.util.{Date => JDate}
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime

case class GridFileAttachment(file: GenericGridFSDBFile) extends Attachment with Entity[Attachment] {

  def data = file.inputStream

  def name = file.filename.get

  def size = Some(file.length)

  def mediaType = file.contentType.flatMap(MIMEType(_))

  def lastModified = new DateTime(file.metaData.getAs[JDate]("last-modified").getOrElse(new JDate()))

  def id = file.id match {
    case null => None
    case i => Some(i.toString)
  }

  def withId(id: String) = {
    file.put("_id", id)
    this
  }
}
