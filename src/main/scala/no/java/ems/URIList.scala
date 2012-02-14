package no.java.ems

import java.net.URI
import java.io._
import io.Source

/**
 * Represents a text/uri-list
 *
 * @author Erlend Hamnaberg<erlend@hamnaberg.net>
 */
case class URIList(list: List[URI]) {
  def writeTo(writer: Writer) = {
    list.foreach( u =>
      writer.write(u.toString + "\r\n")
    )
  }
}

object URIList {
  def parse(source: Source): Either[Exception, URIList] = {
    try {
      Right(URIList(source.getLines().map(line => URI.create(line.trim())).toList))
    }
    catch {
      case e: Exception => Left(e)
    }
    finally {
      source.close()
    }
  }

}
