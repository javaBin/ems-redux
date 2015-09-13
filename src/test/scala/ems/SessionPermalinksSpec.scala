package ems

import org.specs2.mutable.Specification
import java.net.URI
import uritemplate._

class SessionPermalinksSpec extends Specification {
  "SessionPermalinks" should {
    "generate correctly" in {
      val eventId = "0e6d98e9-5b06-42e7-b275-6abadb498c81"
      val expected = URI.create("http://test.2015.javazone.no/details.html?talk=fd09fba268aa5329705298f5f456bb01673edb85ab4a5c6233b3a8d8658ab3cb")
      val permalinks = SessionPermalinks(Map(eventId -> URITemplate("http://test.2015.javazone.no/details.html?talk={href}")))
      val exp = permalinks.expand(eventId, URI.create("http://javazone.no/ems/server/events/0e6d98e9-5b06-42e7-b275-6abadb498c81/sessions/89e097c8-b94e-47fa-9aef-f0b13931cbf5"))
      exp.get must be equalTo(expected)
    }
  }
}
