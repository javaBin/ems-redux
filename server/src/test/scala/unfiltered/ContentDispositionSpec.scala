package unfilteredx

import org.specs2.mutable.Specification
import io.Codec

class ContentDispositionSpec extends Specification {
  "Content Disposition" should {
    "generate simple inline correctly" in {
      val expected  = "inline"
      ContentDisposition(DispositionType.INLINE).toString() must be equalTo(expected)
    }

    "Generate attachment correctly from example in rfc" in {
      val expected = """attachment; filename="genome.jpeg""""
      val actual = ContentDisposition(
        DispositionType.ATTACHMENT,
        Some("genome.jpeg")
      )

      actual.toString() must be equalTo(expected)
    }

    "Parse inline header value correctly from example in rfc" in {
      val input = """INLINE; FILENAME= "an example.html""""
      val expected = ContentDisposition(
        DispositionType.INLINE,
        Some("an example.html")
      )

      ContentDisposition(input) must be equalTo(Some(expected))
    }
    "Parse attachment header value correctly from example in rfc" in {
      val input = """attachment;
                          filename*= UTF-8''%e2%82%ac%20rates"""
      val expected = ContentDisposition(
        DispositionType.ATTACHMENT,
        None,
        Some(CharsetFilename("€ rates", Some(Codec.UTF8)))
      )

      ContentDisposition(input) must be equalTo(Some(expected))
    }
  }
}