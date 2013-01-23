package unfiltered

import request.StringHeader
import unfiltered.response._
import javax.servlet.http.HttpServletResponse


case class DateResponseHeader(name: String, dateTime: Long) extends Responder[HttpServletResponse] {
  def respond(res: HttpResponse[HttpServletResponse]) {
    res.underlying.setDateHeader(name, dateTime)
  }
}


object IfUnmodifiedSinceString extends StringHeader("If-Unmodified-Since")