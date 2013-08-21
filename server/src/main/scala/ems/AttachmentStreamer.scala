package ems

import unfiltered.response.{ContentType, ResponseStreamer}
import java.io.{InputStream, OutputStream}
import unfilteredx.{DispositionType, ContentDisposition}
import ems.storage.BinaryStorage

object AttachmentStreamer {
  def apply(attachment: Attachment, storage: BinaryStorage) = {
    val ct: MIMEType = attachment.mediaType.filterNot(_ == MIMEType.OctetStream).orElse(MIMEType.fromFilename(attachment.name.toLowerCase)).getOrElse(MIMEType.OctetStream)
    ContentType(ct.toString) ~> ContentDisposition(DispositionType.ATTACHMENT, Some(attachment.name)).toResponseHeader ~> new ResponseStreamer {
      def stream(os: OutputStream) {
        val stream = storage.getStream(attachment)
        Streaming.copy(stream, os, closeOS = false)
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