package ems.util

import ems._
import org.specs2.mutable.Specification
import java.net.URI

class URIBuilderSpec extends Specification {
  "A URIBuilder" should {
    "be able to parse URI" in {
      val uri = URI.create("http://example.com?foo=bar")
      val build = URIBuilder(uri).build()
      uri must beEqualTo(build)
    }
    "be able to build expected URI" in {
      val uri = URI.create("http://example.com/woff?foo=bar")
      val build = URIBuilder.empty.withScheme("http").withHost("example.com").path("/woff").queryParam("foo", "bar").build()
      val build2 = URIBuilder.empty.withScheme("http").withHost("example.com").segments("woff").queryParam("foo", "bar").build()
      println(UUIDFromStringOpt("b69a7189-5895-4571-a10a-7620b9ab2df6"))

      uri must beEqualTo(build)
      uri must beEqualTo(build2)
    }
  }
}
