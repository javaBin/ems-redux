package ems

import unfiltered.response.{ContentType, ResponseStreamer}
import java.io.{InputStream, OutputStream}
import unfilteredx.{DispositionType, ContentDisposition}
import ems.storage.BinaryStorage
import com.sksamuel.scrimage.Image

object AttachmentStreamer {
  def apply(attachment: Attachment, storage: BinaryStorage, size: Option[String] = None) = {
    val ct: MIMEType = attachment.mediaType.filterNot(_ == MIMEType.OctetStream).orElse(MIMEType.fromFilename(attachment.name.toLowerCase)).getOrElse(MIMEType.OctetStream)

    ContentType(ct.toString) ~> ContentDisposition(DispositionType.ATTACHMENT, Some(attachment.name)).toResponseHeader ~> new ResponseStreamer {
      def stream(os: OutputStream) {
        val stream = storage.getStream(attachment)
        val sz = size.flatMap(ImageSize)
        sz match {
          case Some(ImageSize(_, width, height)) if MIMEType.ImageAll.includes(ct) => {
            Image(stream).scaleTo(width, height).write(os)
          }
          case _ =>
            Streaming.copy(stream, os, closeOS = false)
        }
      }
    }
  }
}

object Streaming {
  def copy(is: InputStream, os: OutputStream, closeOS: Boolean = true) {
    try {
      val buffer = new Array[Byte](1024 * 4)
      var read = 0
      while({read = is.read(buffer); read != -1}) {
        os.write(buffer, 0, read)
      }
    }
    finally {
      if (is != null) is.close()
      if (os != null && closeOS) os.close()
    }
  }
}

sealed abstract class ImageSize(val name: String, val width: Int, val height: Int) {
  override def toString = width + "x" + height
}

object ImageSize extends ((String) => Option[ImageSize]) {
  def apply(name: String): Option[ImageSize] = name match {
    case Thumb.name => Some(Thumb)
    case _ => None
  }

  def unapply(is: ImageSize) = Some((is.name, is.width, is.height))

  case object Thumb extends ImageSize("thumb", 100, 100)
}
