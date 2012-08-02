package no.java.util

import org.specs2.mutable.Specification
import java.net.URI

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

class URIBuilderSpec extends Specification {
  "A URIBuilder" should {
    "be able to parse URI" in {
      val uri = URI.create("http://example.com?foo=bar")
      val build = URIBuilder(uri).build()
      uri must beEqualTo(build)
    }
  }
}
