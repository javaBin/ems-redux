package ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan
import dispatch._
import unfiltered.filter.request.ContextPath
import java.io.{InputStream, OutputStream}
import com.ning.http.client.RequestBuilder
import com.ning.http.client.Request.EntityWriter
import org.apache.commons.codec.binary.Base64

object EmsProxy extends Plan{
  val Accept = "application/vnd.collection+json,*/*;q=0.8"

  def intent = {
    case req@ContextPath(_, Seg("ajax" :: Nil)) & Params(p) => {
      p("href").headOption.map { href =>
        if (href.contains(EmsConfig.server.toString)) doRequest(req, href) else Forbidden
      }.getOrElse(NotFound)
    }
  }

  private def doRequest[A, B](req: HttpRequest[A], href: String): ResponseFunction[B] = {
    val promise = for {
      res <- Http(makeReq(req, url(href)))
    } yield {
      Status(res.getStatusCode) ~> ContentType(res.getContentType) ~> new ResponseStreamer {
        def stream(os: OutputStream) {
          Streaming.copy(res.getResponseBodyAsStream, os, false)
        }
      }
    }
    promise()
  }

  private def makeReq[A](req: HttpRequest[A], builder: RequestBuilder) = {
    builder.setMethod(req.method)
    req.headerNames.filterNot(_.contains("Cookie")).foreach(hn => req.headers(hn).foreach(v => builder.addHeader(hn, v)))
    builder.setHeader("Accept", Accept)
    builder.setHeader("User-Agent", "Cake")
    builder.setFollowRedirects(true)

    req match {
      case EmsLoginHandler(User(u, p)) => {
        val header = Base64.encodeBase64String(s"$u:$p".getBytes("UTF-8")).trim
        builder.setHeader("Authorization", s"Basic $header")
      }
      case _ => builder
    }
    req match {
      case POST(_) | PUT(_) => builder.setBody(new EntityWriter {
        def writeEntity(out: OutputStream) {
          Streaming.copy(req.inputStream, out)
        }
      })
      case _ => builder
    }
  }
}

object Streaming {
  def copy(is: InputStream, os: OutputStream, closeOS: Boolean = true) {
    try {
      val buffer = new Array[Byte](1024 * 4)
      var read = 0
      while({read = is.read(buffer); read != -1}) {
        os.write(buffer, 0, read)
      }
    }
    finally {
      if (is != null) is.close()
      if (os != null && closeOS) os.close()
    }
  }
}