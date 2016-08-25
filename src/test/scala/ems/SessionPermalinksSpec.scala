package ems

import org.specs2.mutable.Specification
import java.net.URI

class SessionPermalinksSpec extends Specification {
  "SessionPermalinks" should {
    "generate correctly in 2015" in {
      val eventId = "0e6d98e9-5b06-42e7-b275-6abadb498c81"
      val expected = URI.create("http://test.2015.javazone.no/details.html?talk=fd09fba268aa5329705298f5f456bb01673edb85ab4a5c6233b3a8d8658ab3cb")
      val permalinks = SessionPermalinks(Map(eventId -> Expansion("href", "http://test.2015.javazone.no/details.html?talk={href}")))
      val exp = permalinks.expandHref(eventId, URI.create("http://javazone.no/ems/server/events/0e6d98e9-5b06-42e7-b275-6abadb498c81/sessions/89e097c8-b94e-47fa-9aef-f0b13931cbf5"))
      exp.get must be equalTo(expected)
    }
    "generate correctly in 2016" in {
      val eventId = "0e6d98e9-5b06-42e7-b275-6abadb498c81"
      val expected = URI.create("https://2016.javazone.no/program/hjelp-vi-skal-kode-funksjonelt-i-java")
      val expected2 = URI.create("https://2016.javazone.no/program/enterprise-programvare-vart-du-skraemt-no")
      val permalinks = SessionPermalinks(Map(eventId -> Expansion("title", "https://2016.javazone.no/program/{title}")))
      val exp = permalinks.expandTitle(eventId, "Hjelp, vi skal kode funksjonelt i Java!")
      val exp2 = permalinks.expandTitle(eventId, "Enterprise programvare!   Vart du skræmt no?")
      exp.get must be equalTo(expected)
      exp2.get must be equalTo(expected2)
    }
  }
}
