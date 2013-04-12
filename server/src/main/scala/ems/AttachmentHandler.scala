package ems

import unfiltered.response._
import unfiltered.request._
import unfiltered.directives._
import Directives._

trait AttachmentHandler extends ResourceHelper {

  def handleAttachment(id: String) = {
    val get = for {
      _ <- GET
      b <- getOrElse(storage.binary.getAttachment(id), NotFound)
    } yield {
      AttachmentStreamer(b, storage.binary)
    }
    val delete = for {
      _ <- DELETE
    } yield {
      storage.binary.removeAttachment(id)
      NoContent
    }

    get | delete
  }
}
