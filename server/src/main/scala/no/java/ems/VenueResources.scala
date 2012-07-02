package no.java.ems

import unfiltered.response._
import no.java.unfiltered.{RequestContentDisposition, RequestURIBuilder, BaseURIBuilder}
import javax.servlet.http.HttpServletRequest
import unfiltered.request._
import net.hamnaberg.json.collection.{Template, JsonCollection, ErrorMessage}
import converters._


/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait VenueResources extends ResourceHelper {
  this: Storage =>

  def handleVenues(request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val output = this.getVenues().map(venueToItem(baseUriBuilder))
        val href = baseUriBuilder.segments("venues").build()
        CollectionJsonResponse(JsonCollection(href, Nil, output))
      }
      case req@POST(RequestContentType(CollectionJsonResponse.contentType)) => {
        NotImplemented
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleVenue(id: String, request: HttpRequest[HttpServletRequest]) = {
    val venue = this.getVenue(id)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(venue, request, (t: Template) => throw new IllegalArgumentException("Fail"), venueToItem(base))
  }
}
