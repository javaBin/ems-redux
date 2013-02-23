package no.java.ems

import net.hamnaberg.json.collection.JsonCollection
import unfiltered.response.{ResponseString, ContentType, ComposeResponse}

object CollectionJsonResponse {
  val contentType = "application/vnd.collection+json"

  import org.json4s.native.JsonMethods._

  def apply(coll: JsonCollection) = {
    new ComposeResponse[Any](ContentType(contentType) ~> ResponseString(compact(render(coll.toJson))))
  }
}
