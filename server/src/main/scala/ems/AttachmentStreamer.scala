package no.java.ems

import storage.MongoDBStorage
import unfiltered.response.ResponseStreamer
import java.io.{InputStream, OutputStream}
import unfilteredx.{DispositionType, ContentDisposition}

object AttachmentStreamer {
  def apply(attachment: Attachment, storage: MongoDBStorage) = {
    ContentDisposition(DispositionType.ATTACHMENT, Some(attachment.name)).toResponseHeader ~> new ResponseStreamer {
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
      Stream.continually(is.read(buffer)).takeWhile(-1 !=).foreach(_ => os.write(buffer))
    }
    finally {
      if (is != null) is.close()
      if (os != null && closeOS) os.close()
    }
  }
}