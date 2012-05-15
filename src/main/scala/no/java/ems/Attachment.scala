package no.java.ems

import org.joda.time.DateTime
import java.io.{FileInputStream, File, ByteArrayInputStream, InputStream}
import java.net.URI
import com.mongodb.casbah.Imports._
import javax.activation.MimeType
import scala.Some
import com.mongodb.casbah.gridfs.{GridFSFile, GridFSDBFile}

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

sealed trait Attachment {
  def name: String
  def size: Option[Long]
  def mediaType: Option[MIMEType]
}

case class GridFileAttachment(file: GridFSFile) extends Attachment with Entity {
  type T = GridFileAttachment

  val data = file match {
    case f: GridFSDBFile => f.inputStream
    case _ => throw new IllegalStateException("No inputstream was available for non-db object")
  }

  def name = file.filename

  def size = Some(file.size)

  def mediaType = MIMEType(file.contentType)

  def lastModified = file.metaData.getAsOrElse[DateTime]("last-modified", new DateTime())

  def id = file.id match {
    case null => None
    case i => Some(i.toString)
  }

  def withId(id: String) = {
    file.put("_id", id)
    this
  }
}

case class URIAttachment(href: URI, name: String, size: Option[Long], mediaType: Option[MIMEType]) extends Attachment {
  def data = href.toURL.openStream()
}

case class StreamingAttachment(name: String, size: Option[Long], mediaType: Option[MIMEType], data: InputStream, lastModified: DateTime = new DateTime()) extends Attachment

case class MIMEType(major: String, minor: String, parameters: Map[String, String] = Map.empty) {
  def includes(mt: MIMEType): Boolean = {
    new MimeType(toString).`match`(mt.toString)
  }

  override def toString = {
    val params = if (parameters.isEmpty) "" else parameters.mkString(";", ";", "")
    "%s/%s".format(major, minor) + params
  }
}

object MIMEType {
  val All = MIMEType("*", "*")
  val ImageAll = MIMEType("image", "*")
  val VideoAll = MIMEType("video", "*")
  val Pdf = MIMEType("application", "pdf")
  val Png = MIMEType("image", "png")
  val Jpeg = MIMEType("image", "jpeg")

  def apply(mimeType: String): Option[MIMEType] = util.control.Exception.allCatch.opt{
    val mime = new MimeType(mimeType)
    import collection.JavaConverters._
    val keys = mime.getParameters.getNames.asInstanceOf[java.util.Enumeration[String]].asScala
    val params = keys.foldLeft(Map[String, String]())((a, b) => a.updated(b, mime.getParameters.get(b)))
    MIMEType(mime.getPrimaryType, mime.getSubType, params)
  }
}