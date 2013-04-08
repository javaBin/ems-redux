package unfilteredx

import java.net.URI
import javax.servlet.http.HttpServletRequest
import util.URIBuilder
import unfiltered.request.{StringHeader, HttpRequest}


object RequestURIBuilder {
  def unapply(req: HttpRequest[HttpServletRequest]) = {
    Some(getBuilder(req))
  }

  def getBuilder(req: HttpRequest[HttpServletRequest]) = {
    val requestURI = req.underlying.getRequestURL
    Option(req.underlying.getQueryString).map("?" + _).foreach(requestURI.append(_))
    URIBuilder(URI.create(requestURI.toString))
  }
}

object BaseURIBuilder {
  def getBuilder(req: HttpRequest[HttpServletRequest]) = {
    val path = req.underlying.getContextPath
    URIBuilder(URI.create(req.underlying.getRequestURL.toString)).replacePath(path)
  }

  def unapply(req: HttpRequest[HttpServletRequest]) = {
    Some(getBuilder(req))
  }
}

object RequestContentDisposition {
  def unapply[T](req: HttpRequest[T]) = {
    val value = new StringHeader(ContentDisposition.headerName).unapply(req)
    value.map(ContentDisposition(_)).flatMap(identity)
  }
}