package unfilteredx

import java.net.URI
import unfiltered.response._

case class LinkHeader(href: URI, rel: String) extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    res.header("Link", "<%s>; rel=\"%s\"".format(href, rel))
  }
}
