package no.java.ems

import org.joda.time.DateTime
import java.io.{FileInputStream, File, InputStream}
import java.net.URI
import javax.activation.{MimetypesFileTypeMap, MimeType}
import scala.Some

trait Attachment {
  def name: String
  def size: Option[Long]
  def mediaType: Option[MIMEType]
}

case class URIAttachment(href: URI, name: String, size: Option[Long], mediaType: Option[MIMEType]) extends Attachment {
  def data = href.toURL.openStream()
}

case class StreamingAttachment(name: String, size: Option[Long], mediaType: Option[MIMEType], data: InputStream, lastModified: DateTime = new DateTime()) extends Attachment

object StreamingAttachment {
  def apply(file: File): StreamingAttachment = StreamingAttachment(
    file.getName,
    Some(file.length()),
    MIMEType.fromFilename(file.getName),
    new FileInputStream(file)
  )
}

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
  private val resolver = new MimetypesFileTypeMap()

  val All = MIMEType("*", "*")
  val ImageAll = MIMEType("image", "*")
  val VideoAll = MIMEType("video", "*")
  val Pdf = MIMEType("application", "pdf")
  val Png = MIMEType("image", "png")
  val Jpeg = MIMEType("image", "jpeg")
  val OctetStream = MIMEType("application", "octet-stream")

  def apply(mimeType: String): Option[MIMEType] = util.control.Exception.allCatch.opt{
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