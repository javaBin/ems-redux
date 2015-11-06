package ems

import java.io.InputStream
import javax.activation.{MimeType, MimetypesFileTypeMap}

import org.joda.time.DateTime

import scala.util.Try

trait Attachment {
  def name: String
  def size: Option[Long]
  def mediaType: Option[MIMEType]
  def lastModified: DateTime
}

case class StreamingAttachment(name: String, size: Option[Long], mediaType: Option[MIMEType], data: InputStream, lastModified: DateTime = new DateTime()) extends Attachment

case class MIMEType(major: String, minor: String, parameters: Map[String, String] = Map.empty) {
  def includes(mt: MIMEType): Boolean = {
    new MimeType(toString).`match`(mt.toString)
  }

  override def toString = {
    val params = if (parameters.isEmpty) "" else parameters.map{case (k,v) => s"$k=$v"}.mkString(";", "; ", "")
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

  def apply(mimeType: String): Option[MIMEType] = Try{
    val mime = new MimeType(mimeType)
    import collection.JavaConverters._
    val keys = mime.getParameters.getNames.asInstanceOf[java.util.Enumeration[String]].asScala
    val params = keys.foldLeft(Map[String, String]())((a, b) => a.updated(b, mime.getParameters.get(b)))
    MIMEType(mime.getPrimaryType, mime.getSubType, params)
  }.toOption

  def fromFilename(filename: String) = {
    apply(resolver.getContentType(filename.toLowerCase))
  }
}
