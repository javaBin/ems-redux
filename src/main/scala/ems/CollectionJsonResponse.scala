package ems

import net.hamnaberg.json.collection.JsonCollection
import unfiltered.response.{ResponseWriter, ContentType, ComposeResponse}
import java.io.OutputStreamWriter

object CollectionJsonResponse {
  val contentType = "application/vnd.collection+json"

  def apply(coll: JsonCollection) = {
    new ComposeResponse[Any](ContentType(contentType) ~> new ResponseWriter {
      def write(writer: OutputStreamWriter) {
        coll.writeTo(writer)
      }
    })
  }
}
