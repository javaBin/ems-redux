package no.java.ems

import javax.servlet.http.HttpServletRequest
import storage.MongoDBStorage
import unfiltered.response.{MethodNotAllowed, NotFound, NoContent}
import unfiltered.request._

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait AttachmentHandler {

  def storage: MongoDBStorage

  def handleAttachment(id: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) => {
        storage.getAttachment(id) match {
          case Some(a) => AttachmentStreamer(a, storage)
          case None => NotFound
        }
      }
      case DELETE(_) => {
        storage.removeAttachment(id)
        NoContent
      }
      case _ => MethodNotAllowed
    }
  }

}
