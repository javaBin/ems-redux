package no.java.unfiltered

import java.net.URI
import javax.servlet.http.HttpServletRequest
import no.java.http.URIBuilder
import unfiltered.request.{StringHeader, HttpRequest}


object RequestURIBuilder {
  def unapply(req: HttpRequest[HttpServletRequest]) = {
    val requestURI = req.underlying.getRequestURL
    Option(req.underlying.getQueryString).map("?" + _).foreach(requestURI.append(_))
    Some(URIBuilder(URI.create(requestURI.toString)))
  }
}

object BaseURIBuilder {
  def unapply(req: HttpRequest[HttpServletRequest]) = {
    val path = req.underlying.getContextPath
    Some(URIBuilder(URI.create(req.underlying.getRequestURL.toString)).replacePath(path))
  }
}

object RequestContentDisposition {
  def unapply[T](req: HttpRequest[T]) = {
    val value = new StringHeader(ContentDisposition.headerName).unapply(req)
    value.map(ContentDisposition(_)).flatMap(identity)
  }
}