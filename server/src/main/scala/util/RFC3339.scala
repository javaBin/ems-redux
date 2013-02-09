package util

import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import org.joda.time.{DateTimeZone, MutableDateTime, DateTime}
import scala.util.control.Exception.allCatch
import java.util.regex.Pattern

object RFC3339 {
  private val pattern = Pattern.compile("(\\d{4})(?:-(\\d{2}))?(?:-(\\d{2}))?(?:([Tt])?(?:(\\d{2}))?(?::(\\d{2}))?(?::(\\d{2}))?(?:\\.(\\d{3}))?)?([Zz])?(?:([+-])(\\d{2}):(\\d{2}))?")
  private val baseDateTime = "yyyy-MM-dd'T'HH:mm:ss"
  private val fullDateTimeUTC = DateTimeFormat.forPattern("%s'Z'".format(baseDateTime)).withZoneUTC()
  private val fullDateTimeMillisUTC = DateTimeFormat.forPattern("%s.SSS'Z'".format(baseDateTime)).withZoneUTC()


  def parseDateTime(input: String): Either[String, DateTime] = {
    if (input.endsWith("Z")) {
      allCatch.opt(fullDateTimeUTC.parseDateTime(input)).
        orElse(allCatch.opt(fullDateTimeMillisUTC.parseDateTime(input))).
        toRight("'%s' does not conform to RFC-3339".format(input))
    }
    else {
      parseSpec(input)
    }
  }

  private def parseSpec(input: String): Either[String, DateTime] = {
    lazy val error = Left("'%s' does not conform to RFC-3339".format(input))
    val m = pattern.matcher(input)
    if (m.find()) {
      if (m.group(4) == null)
        error
      var hoff = 0
      var moff = 0
      if (m.group(10) != null) {
        val doff = if (m.group(10).equals("-")) 1 else -1
        hoff = doff * (Option(m.group(11)).map(_.toInt).getOrElse(0))
        moff = doff * (Option(m.group(12)).map(_.toInt).getOrElse(0))
      }
      Right(new DateTime(
        m.group(1).toInt,
        Option(m.group(2)).map(_.toInt).getOrElse(0),
        Option(m.group(3)).map(_.toInt).getOrElse(1),
        Option(m.group(5)).map(_.toInt + hoff).getOrElse(0),
        Option(m.group(6)).map(_.toInt + moff).getOrElse(0),
        Option(m.group(7)).map(_.toInt).getOrElse(0),
        Option(m.group(8)).map(_.toInt).getOrElse(0),
        DateTimeZone.UTC
      ))
    }
    else {
      error
    }
  }

  def format(dt: DateTime) = dt.toString(fullDateTimeUTC)
}
