package unfilteredx

import unfiltered.request.StringHeader
import unfiltered.response._
import org.joda.time.{DateTimeZone, DateTime}
import java.util.Locale


case class DateResponseHeader(name: String, dateTime: DateTime) extends Responder[Any] {
  val PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"

  def respond(res: HttpResponse[Any]) {
    res.header(name, dateTime.withMillisOfSecond(0).withZone(DateTimeZone.UTC).toString(PATTERN_RFC1123, Locale.ENGLISH))
  }
}


object IfUnmodifiedSinceString extends StringHeader("If-Unmodified-Since")
object IfModifiedSinceString extends StringHeader("If-Modified-Since")