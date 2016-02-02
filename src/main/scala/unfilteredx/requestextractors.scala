package unfilteredx

import java.net.URI
import javax.servlet.http.HttpServletRequest
import ems.util.URIBuilder
import unfiltered.request._


object RequestURIBuilder {
  def unapply[A](req: HttpRequest[A]): Option[URIBuilder] = {
    Some(apply(req))
  }

  def apply[A](req: HttpRequest[A]) = {
    val (scheme, host, port) = req match {
      case XForwardedProto(proto) & HostPort(h, _) => (proto, h, None)
      case HostPort(h, 80) => ("http", h, None)
      case HostPort(h, 443) => ("https", h, None)
      case HostPort(h, p) => (if (req.isSecure) "https" else "http", h, Some(p))
      case _ => sys.error("No Host header!!!!")
    }
    val path = Path.apply(req)

    URIBuilder(Some(scheme), Some(host), port, Nil, QueryParams.unapply(req).getOrElse(Map.empty)).path(path)
  }
}

object RequestURI {
  def unapply[A](req: HttpRequest[A]) : Option[URI] = Some(apply(req))
  def apply[A](req: HttpRequest[A]) = RequestURIBuilder(req).build()
}

object BaseURIBuilder {

  def apply(req: HttpRequest[Any]) = {
    val path = req.underlying match {
      case r: HttpServletRequest => Option(r.getContextPath).getOrElse(System.getProperty("contextPath", "/server"))
      case _ => "/"
    }
    RequestURIBuilder(req).emptyParams().replacePath(path)
  }

  def unapply(req: HttpRequest[Any]): Option[URIBuilder] = Some(apply(req))
}

object BaseURI {
  def unapply[A](req: HttpRequest[A]): Option[URI] = Some(apply(req))
  def apply[A](req: HttpRequest[A]) = BaseURIBuilder(req).build()
}

object RequestContentDisposition {
  def unapply[T](req: HttpRequest[T]) = {
    val value = new StringHeader(ContentDisposition.headerName).unapply(req)
    value.map(ContentDisposition(_)).flatMap(identity)
  }
}
