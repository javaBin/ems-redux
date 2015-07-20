package ems

import unfiltered.response._
import unfiltered.request._
import unfiltered.directives._
import Directives._
import ems.security.User
import com.sksamuel.scrimage.Image
import unfilteredx._

trait AttachmentHandler extends ResourceHelper {

  def handleAttachment(id: UUID)(implicit user: User) = {
    def stream(att: Attachment, params: Map[String, Seq[String]]) = {
      DateResponseHeader("Last-Modified", att.lastModified) ~> AttachmentStreamer(att, storage.binary, params("size").headOption)
    }

    val get = for {
      _ <- GET
      b <- getOrElse(storage.binary.getAttachment(id), NotFound)
      params <- queryParams
      res <- ifModifiedSince(b.lastModified, stream(b, params))
    } yield res

    val delete = for {
      _ <- DELETE
      _ <- authenticated(user)
    } yield {
      storage.binary.removeAttachment(id)
      NoContent
    }

    get | delete
  }
}
