package no.java.ems

import unfiltered.response.ResponseStreamer
import java.io.OutputStream
import no.java.unfiltered.{DispositionType, ContentDisposition}

object AttachmentStreamer {
  def apply(attachment: Attachment) = {
    new ResponseStreamer {
      val buf = new Array[Byte](1024 * 8)

      def stream(os: OutputStream) {
        val length = attachment.data.read(buf)
        os.write(buf, 0, length)
      }
    } ~> ContentDisposition(DispositionType.ATTACHMENT, Some(attachment.name)).toResponseHeader
  }
}
