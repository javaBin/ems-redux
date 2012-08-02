package no.java.ems

import storage.MongoDBStorage
import unfiltered.response.ResponseStreamer
import java.io.OutputStream
import unfilteredx.{DispositionType, ContentDisposition}
import org.apache.commons.io.IOUtils

object AttachmentStreamer {
  def apply(attachment: Attachment, storage: MongoDBStorage) = {
    new ResponseStreamer {
      def stream(os: OutputStream) {
        val stream = storage.getStream(attachment)
        try {
          IOUtils.copy(stream, os)
        }
        finally {
          IOUtils.closeQuietly(stream)
        }
      }
    } ~> ContentDisposition(DispositionType.ATTACHMENT, Some(attachment.name)).toResponseHeader
  }
}
