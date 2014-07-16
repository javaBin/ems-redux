package ems

import unfiltered.response._
import unfiltered.request._
import unfiltered.directives._
import Directives._
import ems.security.User
import com.sksamuel.scrimage.Image

trait AttachmentHandler extends ResourceHelper {

  def handleAttachment(id: String)(implicit user: User) = {
    val get = for {
      _ <- GET
      b <- getOrElse(storage.binary.getAttachment(id), NotFound)
      params <- queryParams
    } yield {
      AttachmentStreamer(b, storage.binary, params("size").headOption)
    }
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