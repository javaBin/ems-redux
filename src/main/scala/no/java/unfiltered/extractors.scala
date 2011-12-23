package no.java.unfiltered

import unfiltered.request.HttpRequest
import java.net.URI
import javax.servlet.http.HttpServletRequest
import no.java.http.URIBuilder


object RequestURIBuilder {
  def unapply(req: HttpRequest[HttpServletRequest]) = {
    val requestURI = req.underlying.getRequestURL
    Option(req.underlying.getQueryString).map("?" + _).foreach(requestURI.append(_))
    Some(URIBuilder(URI.create(requestURI.toString)))
  }
}

object BaseURIBuilder {
  def unapply(req: HttpRequest[HttpServletRequest]) = Some(URIBuilder(URI.create(req.underlying.getRequestURL.toString)).emptyPath())
}

object IFNoneMatch {
  def unapply(req: HttpRequest[HttpServletRequest]) = Option(req.underlying.getHeader("If-None-Match"))
}

object IFMatch {
  def unapply(req: HttpRequest[HttpServletRequest]) = Option(req.underlying.getHeader("If-Match"))
}

object IfModifiedSince {
  def unapply(req: HttpRequest[HttpServletRequest]) = Option(req.underlying.getHeader("If-Modified-Since"))
}

object IfUnmodifiedSince {
  def unapply(req: HttpRequest[HttpServletRequest]) = Option(req.underlying.getHeader("If-Unmodified-Since"))
}