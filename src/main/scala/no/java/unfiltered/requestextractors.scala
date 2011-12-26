package no.java.unfiltered

import java.net.URI
import javax.servlet.http.HttpServletRequest
import no.java.http.URIBuilder
import unfiltered.request.{StringHeader, HttpRequest}
import java.util.Scanner


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

object IfNoneMatch extends StringHeader("If-None-Match")

object IfMatch extends StringHeader("If-Match")

object IfModifiedSince extends StringHeader("If-Modified-Since")

object IfUnmodifiedSince extends StringHeader("If-Unmodified-Since")

object RequestContentDisposition {
  private def tokenize: (String) => Map[String, Option[String]] = (s) => {
    val scanner = new Scanner(s).useDelimiter(",")
    var map = Map[String,  Option[String]]()
    while(scanner.hasNext) {
      val token = scanner.next().trim()
      val namevalue = token.split("=", 2)
      val value = if (namevalue.length == 2) (namevalue(0).trim(), Some(namevalue(1).trim())) else (namevalue(0).trim(), None)
      map += value
    }
    map
  }

  def unapply[T](req: HttpRequest[T]) = {
    val value = new StringHeader(ContentDisposition.headerName).unapply(req)
    value.map(tokenize).map(m => ContentDisposition(m))
  }
}