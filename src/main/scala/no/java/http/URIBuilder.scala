package no.java.http

import java.net.{URLDecoder, URI, URLEncoder}


/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 12/20/11
 * Time: 10:59 AM
 * To change this template use File | Settings | File Templates.
 */

case class URIBuilder private(scheme: Option[String], host: Option[String], port: Option[Int], path: List[Segment], params: Map[String, List[String]], pathEndsWithSlash: Boolean = false) {
  def withScheme(scheme: String) = copy(scheme = Some(scheme))
  
  def withHost(host: String) = copy(host = Some(host))
  def withPort(port: Int) = copy(port = Some(port))

  def segments(segments: String*) = copy(path = path ::: segments.map(Segment(_)).toList)

  def path(path: String): URIBuilder = {
    val segments = path.split("/").map(Segment.decoded(_)).toList
    copy(path = this.path ::: segments, pathEndsWithSlash = path.endsWith("/"))
  }

  def replacePath(path: String): URIBuilder = {
    val segments = path.split("/").map(Segment.decoded(_)).toList
    copy(path = segments, pathEndsWithSlash = path.endsWith("/"))
  }
  
  def emptyPath() = copy(path = Nil)

  def emptyParams() = copy(params = Map.empty)

  def queryParam(name: String, value: String) = copy(params = params + (name -> params.get(name).map(x => x ::: List(value)).getOrElse(List(value))))

  def replaceSegments(segments: Segment*) = copy(path = segments.toList)

  def replaceQueryParam(name: String, value:String) = copy(params = params + (name -> List(value)))
  
  def build() = {
    val par = if (params.isEmpty) None else Some(params)
    new URI(
      scheme.getOrElse(null),
      null,
      host.getOrElse(null),
      port.getOrElse(-1),
      path.map(_.encoded).mkString("/", "/", if (pathEndsWithSlash) "/" else ""),
      par.map(_.map{case (x,y) => y.map((x,_))}.mkString("", "&", "")).getOrElse(null),
      null
    )
  }
  override def toString = build().toString
}

object URIBuilder {
  def apply(uri: URI): URIBuilder = {
    val endsWithSlash = Option(uri.getPath).map(_.endsWith("/")).getOrElse(false)
    val path = Option(uri.getPath).map(_.split("/").map(Segment.decoded(_)).toList).getOrElse(Nil)
    new URIBuilder(Option(uri.getScheme), Option(uri.getHost), Option(uri.getPort).filterNot(_ == -1), path, Map(), endsWithSlash)
  }
  
  def fromPath(path: String): URIBuilder = {
    empty.path(path)
  }

  def empty = new URIBuilder(None, None, None, Nil, Map())
}

case class Segment(seg: String) {
  def encoded = URLEncoder.encode(seg, "UTF-8")
}

object Segment {
  def decoded(seg: String) = Segment(URLDecoder.decode(seg, "UTF-8"))
}

