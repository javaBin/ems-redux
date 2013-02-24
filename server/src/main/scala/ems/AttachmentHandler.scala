package ems

import javax.servlet.http.HttpServletRequest
import storage.MongoDBStorage
import unfiltered.response.{MethodNotAllowed, NotFound, NoContent}
import unfiltered.request._
import ems.storage.BinaryStorage

trait AttachmentHandler {

  def storage: MongoDBStorage

  def handleAttachment(id: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) => {
        storage.binary.getAttachment(id) match {
          case Some(a) => AttachmentStreamer(a, storage.binary)
          case None => NotFound
        }
      }
      case DELETE(_) => {
        storage.binary.removeAttachment(id)
        NoContent
      }
      case _ => MethodNotAllowed
    }
  }

}
