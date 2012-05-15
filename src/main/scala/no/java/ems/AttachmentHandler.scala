package no.java.ems

import javax.servlet.http.HttpServletRequest
import unfiltered.response.{MethodNotAllowed, NotFound, NoContent}
import unfiltered.request._

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait AttachmentHandler {this: Storage =>

  def handleAttachment(id: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) => {
        this.getAttachment(id) match {
          case Some(a) => AttachmentStreamer(a, this)
          case None => NotFound
        }
      }
      case DELETE(_) => {
        this.removeAttachment(id)
        NoContent
      }
      case _ => MethodNotAllowed
    }
  }

}
