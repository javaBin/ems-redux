package no.java.unfiltered

import unfiltered.response.ResponseHeader

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

case class ContentDisposition(name: String) {
  override def toString = {
    "name=%s".format(name)
  }

  def toResponseHeader = ResponseHeader("Content-Disposition", List(toString))
}

object ContentDisposition {
  def apply(map: Map[String, Option[String]]): ContentDisposition = {
    ContentDisposition(map.get("name").flatMap(identity).get)
  }
}