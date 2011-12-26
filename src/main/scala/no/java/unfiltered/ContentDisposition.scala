package no.java.unfiltered

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

case class ContentDisposition(name: String) {
  override def toString = {
    "name=%s".format(name)
  }
}

object ContentDisposition {
  def apply(map: Map[String, Option[String]]): ContentDisposition = {
    ContentDisposition(map.get("name").flatMap(identity).get)
  }
}