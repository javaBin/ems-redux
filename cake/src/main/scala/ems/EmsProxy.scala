package ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan
import dispatch._
import unfiltered.filter.request.ContextPath
import java.io.{InputStream, OutputStream}

object EmsProxy extends Plan{
  val Accept = "application/vnd.collection+json,*/*;q=0.8"

  def intent = {
    case ContextPath(_, Seg("ajax" :: Nil)) & Params(p) => {
      p("href").headOption.map { href =>
        if (href.startsWith(EmsConfig.server.toString)) doRequest(href) else Forbidden
      }.getOrElse(NotFound)
    }
  }

  private def doRequest[B](href: String): ResponseFunction[B] = {
    val promise = for {
      res <- Http(url(href) <:< Map("Accept" -> Accept))
    } yield {
      println(res.getStatusCode)
      ContentType(res.getContentType) ~> new ResponseStreamer {
        def stream(os: OutputStream) {
          Streaming.copy(res.getResponseBodyAsStream, os, false)
        }
      }
    }
    promise()
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