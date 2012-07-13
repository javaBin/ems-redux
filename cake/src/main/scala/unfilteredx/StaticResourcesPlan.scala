package unfilteredx

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

package unfilteredx

import unfiltered.request._
import unfiltered.response._
import resource.Resource
import io.Source
import java.io.{InputStream, OutputStream}
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import java.util.Locale
import unfiltered.filter.Plan

object StaticResourcesPlan extends Plan {
  val Bootstrap = "bootstrap-2.0.4"

  def intent = {
    case GET(Path(Seg("css" :: file))) => serveStatic("css", file.mkString("/"), CssContent)
    case GET(Path(Seg(Bootstrap :: "css" :: file))) => serveStatic("%s/css".format(Bootstrap), file.mkString("/"), CssContent)
    case GET(Path(Seg("js" :: file))) => serveStatic("js", file.mkString("/"), JsonContent)
    case GET(Path(Seg(Bootstrap :: "js" :: file))) => serveStatic("%s/js".format(Bootstrap), file.mkString("/"), JsContent)
    case GET(Path(Seg("img" :: file))) => serveImage("img", file.mkString("/"))
    case GET(Path(Seg(Bootstrap :: "img" :: file))) => serveImage("%s/img".format(Bootstrap), file.mkString("/"))
  }


  def serveImage(root: String, file: String): Object with ResponseFunction[Any] = {
    val resource = Resource("classpath:" + root + "/" + file)
    if (resource.exists) {
      val lastModified = resource.file().map(file => new DateTime(file.lastModified())).getOrElse(new DateTime)
      val expires = 3600 * 24 * 30
      resource.inputStream() match {
        case Left(_) => InternalServerError
        case Right(is) =>
          Ok ~> cacheHeaders(lastModified, expires) ~> ContentType(Mime.unapply(file).getOrElse("image/png")) ~> ImageStreamer(is)
      }
    } else {
      NotFound
    }
  }

  def serveStatic(root: String, file: String, contentType: BaseContentType) = {
    val resource: Resource = Resource("classpath:" + root + "/" + file)
    if (resource.exists) {
      val lastModified = resource.file().map(file => new DateTime(file.lastModified())).getOrElse(new DateTime)
      val expires = 3600 * 24 * 30
      resource.inputStream() match {
        case Left(_) => InternalServerError
        case Right(is) => Ok ~> cacheHeaders(lastModified, expires) ~> contentType ~> ResponseString(Source.fromInputStream(is).mkString)
      }
    } else {
      NotFound
    }
  }


  def cacheHeaders(lastModified: DateTime, maxAge: Int) = {
    val pattern = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").
      withZone(DateTimeZone.forID("GMT")).withLocale(Locale.ENGLISH)
    CacheControl("max-age=%s".format(maxAge)) ~> LastModified(lastModified.toString(pattern))
  }

  case class ImageStreamer(is: InputStream) extends ResponseStreamer {
    import collection.immutable.Stream._
    def stream(os: OutputStream) {
      val buf = new Array[Byte](1024 * 8)
      continually(is.read(buf)).takeWhile(_ != -1).foreach(_ => os.write(buf))
    }
  }

}