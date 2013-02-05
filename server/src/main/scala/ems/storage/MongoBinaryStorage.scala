package ems.storage

import no.java.ems.{StreamingAttachment, URIAttachment, Attachment}
import no.java.ems.model.Entity
import com.mongodb.casbah.gridfs.{GenericGridFSDBFile, GridFS}
import com.mongodb.casbah.Imports._
import no.java.ems.storage.{FileAttachment, GridFileAttachment}
import java.io.InputStream
import java.util.{Date => JDate, UUID}

trait MongoBinaryStorage extends BinaryStorage {
  def db: MongoDB

  def getAttachment(id: String): Option[Attachment with Entity[Attachment]] = {
    val fs = GridFS(db)
    fs.findOne(MongoDBObject("_id" -> id)).map(GridFileAttachment)
  }

  def saveAttachment(att: Attachment) = {
    val fs = GridFS(db)

    val file = att match {
      case GridFileAttachment(f) => Some(f)
      case a@FileAttachment(Some(id),_ ,_ ,_) => fs.findOne(MongoDBObject("_id" -> id)).orElse(createInputFromAttachment(a))
      case a => createInputFromAttachment(a)
    }

    GridFileAttachment(file.get)
  }

  //TODO: Make sure that we remove where its used as well.
  def removeAttachment(id: String) {
    val fs = GridFS(db)
    fs.remove(id)
  }

  override def getStream(att: Attachment): InputStream = att match {
    case u: URIAttachment => u.data
    case u: GridFileAttachment => u.data
    case u: StreamingAttachment => u.data
    case u: FileAttachment => u.data
    case _ => throw new IllegalArgumentException("No stream available for %s".format(att.getClass.getName))
  }

  private def createInputFromAttachment(a: Attachment): Option[GenericGridFSDBFile] = {
    val fs = GridFS(db)
    val id = fs(getStream(a)) { f =>
      f.filename = a.name
      a.mediaType.foreach(mt => f.contentType = mt.toString)
      f.underlying.setId(UUID.randomUUID().toString)
      f.metaData = MongoDBObject("last-modified" -> new JDate())
    }
    fs.findOne(MongoDBObject("_id" -> id.get))
  }

}
