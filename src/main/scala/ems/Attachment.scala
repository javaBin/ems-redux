package ems

import model.Entity
import org.joda.time.DateTime
import java.io.{FileInputStream, File, InputStream}
import java.net.URI
import java.util.{Date => JDate}
import javax.activation.{MimetypesFileTypeMap, MimeType}
import com.mongodb.casbah.Imports._

trait Attachment {
  def name: String
  def size: Option[Long]
  def mediaType: Option[MIMEType]
  def lastModified: DateTime
}

case class URIAttachment(id: Option[UUID], href: URI, name: String, size: Option[Long], mediaType: Option[MIMEType], lastModified: DateTime = new DateTime()) extends Attachment with Entity[Attachment] {
  def data = href.toURL.openStream()
  def withId(id: UUID) = copy(id = Some(id))
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
  private val resolver = new MimetypesFileTypeMap(classOf[MIMEType].getResourceAsStream("/META-INF/mime.types"))

  val All = MIMEType("*", "*")
  val ImageAll = MIMEType("image", "*")
  val VideoAll = MIMEType("video", "*")
  val Pdf = MIMEType("application", "pdf")
  val Png = MIMEType("image", "png")
  val Jpeg = MIMEType("image", "jpeg")
  val OctetStream = MIMEType("application", "octet-stream")

  def apply(mimeType: String): Option[MIMEType] = scala.util.control.Exception.allCatch.opt{
    val mime = new MimeType(mimeType)
    import collection.JavaConverters._
    val keys = mime.getParameters.getNames.asInstanceOf[java.util.Enumeration[String]].asScala
    val params = keys.foldLeft(Map[String, String]())((a, b) => a.updated(b, mime.getParameters.get(b)))
    MIMEType(mime.getPrimaryType, mime.getSubType, params)
  }

  def fromFilename(filename: String) = {
    apply(resolver.getContentType(filename.toLowerCase))
  }
}
