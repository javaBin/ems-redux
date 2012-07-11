package no.java.ems

import unfiltered.response.ResponseStreamer
import java.io.OutputStream
import unfilteredx.{DispositionType, ContentDisposition}
import org.apache.commons.io.IOUtils

object AttachmentStreamer {
  def apply(attachment: Attachment, storage: Storage) = {
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
