package ems
package storage

import java.io.{FileOutputStream, FileInputStream, File}
import java.util.{UUID, Properties}
import org.apache.commons.io.IOUtils

class FilesystemBinaryStorage(val baseDirectory: File) extends BinaryStorage {
  if (!baseDirectory.exists && !baseDirectory.mkdirs()) {
    throw new IllegalStateException(baseDirectory.getAbsolutePath + " did not exist, and we were unable to create it. ")
  }

  def getAttachment(id: UUID) = {
    val file = Some(getFile(id)).filter(f => f.exists && f.canRead)
    file.map{ f =>
      val metadata = loadMetadata(id)
      FileAttachment(Some(id), f, metadata("name"), metadata.get("media-type").flatMap(MIMEType(_)))
    }
  }

  def saveAttachment(att: Attachment) = {
    val id = att match {
      case x: FileAttachment => x.id.getOrElse(randomUUID)
      case x => randomUUID
    }
    val file = getFile(id)
    val parent = file.getParentFile
    if (!parent.exists() && !parent.mkdirs()) {
      throw new IllegalStateException("Failed to create folder for parent")
    }
    val output = new FileOutputStream(file)
    val stream = getStream(att)
    try {
      IOUtils.copy(stream, output)
      saveMetadata(att, getMetadataFile(id))
    }
    finally {
      IOUtils.closeQuietly(stream)
      IOUtils.closeQuietly(output)
    }
    getAttachment(id).get
  }


  def removeAttachment(id: UUID) {
    val file = getFile(id)
    if (file.exists && file.canWrite) {
      getMetadataFile(id).delete
      file.delete
    }
    val list = file.getParentFile.list()
    if (list == null || list.isEmpty) {
      file.getParentFile.delete()
    }
  }

  private def getMetadataFile(id: UUID): File = new File(getFile(id).getAbsolutePath + ".metadata")

  private def getFile(id: UUID): File = new File(new File(baseDirectory, id.toString.substring(0, 2)), id)

  private def loadMetadata(id: UUID): Map[String, String] = {
    import collection.JavaConverters._
    val properties = {
      val p = new Properties()
      val stream = new FileInputStream(getMetadataFile(id))
      try {
        p.load(stream)
      }
      finally {
        stream.close()
      }
      p
    }
    properties.asScala.toMap
  }

  private def saveMetadata(a: Attachment, file: File) = {
    val p = new Properties()
    p.setProperty("name", a.name)
    a.mediaType.foreach(mt => p.setProperty("media-type", mt.toString))
    p.store(new FileOutputStream(file), null)
  }
}
