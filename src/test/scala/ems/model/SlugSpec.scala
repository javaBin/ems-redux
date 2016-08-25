package ems.model

import org.specs2.mutable.Specification

class SlugSpec extends Specification {
  "A Slug" should {
    "be generated" in {
      val title = "Slug slug æøå hei hei hei   meh"

      val expected = "slug_slug__hei_hei_hei___meh"

      val slug = Slug.makeSlug(title)
      slug must be equalTo(expected)
    }
  }
}
