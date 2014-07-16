package ems

import unfiltered.request.{HttpRequest, Accepts}


object AcceptCollectionJson extends Accepts.Accepting {
  val contentType = CollectionJsonResponse.contentType
  val ext = "json"

  override def unapply[T](r: HttpRequest[T]) = Accepts.Json.unapply(r) orElse super.unapply(r)
}
