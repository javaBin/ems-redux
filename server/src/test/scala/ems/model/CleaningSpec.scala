package ems.model

import org.specs2.mutable.Specification

class CleaningSpec extends Specification {
  "A string containing html" should {
    "containing norwegian chars after cleaning" in {
      val input =
        """
          |<div class="bar">
          | blåbær på tur i skogen
          |</div>
        """.stripMargin

      val noHtml = input.noHtml
      noHtml.trim must beEqualTo("blåbær på tur i skogen")
    }
  }
}
