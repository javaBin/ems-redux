package no.java

import http.URIBuilder
import java.net.URI

package object ems {
  implicit def toRichURI(uri: URI) = RichURI(uri)

  implicit def fromRichURI(uri: RichURI) = uri.uri

  implicit def toURI(uri: String) = URI.create(uri)

  case class RichURI(uri: URI) {
    def builder = URIBuilder(uri)
  }
}