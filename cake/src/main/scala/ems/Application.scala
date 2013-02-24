package ems

import crypto.DES
import unfiltered.filter.Plan
import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.request.ContextPath
import io.Source
import dispatch._
import java.net.URI
import unfiltered.Cookie
import org.apache.commons.codec.binary.Base64
import javax.servlet.http.HttpServletRequest

class Application extends Plan {
  def intent = {
    case ContextPath(cp, "/") => {
      val index = Source.fromInputStream(config.getServletContext.getResourceAsStream("/index.html")).getLines().mkString
      HtmlContent ~> ResponseString(index.replace("[EMS_SERVER]", Config.default.server.toString))
    }

    case req@ContextPath(_, Seg("login" :: Nil)) & UserAndPassword(u, p) & HostPort(h, port) => {
      val host = if (h == "localhost") None else Some(h)
      val hasHost = Config.default.server.getHost != null
      val href = if (!hasHost) {
        URI.create(req.underlying.getRequestURL.toString).resolve(Config.default.server)
      }
      else {
        Config.default.server
      }
      val response = Http(url(href.toString + "?auth=true") as(u, p))()

      if (response.getStatusCode == 200) {
       Ok ~> SetCookies(
          Cookie("username", u, host, maxAge = Some(24 * 3600), httpOnly = false),
          Cookie("login", Base64.encodeBase64String(DES.encrypt(s"$u:$p".getBytes("UTF-8"), Config.default.password)).trim, host, maxAge = Some(24 * 3600), httpOnly = true)
        )
      }
      else {
        logout(host)
      }
    }
    case req@ContextPath(_, Seg("logout" :: Nil)) & HostPort(h, port) => {
      val host = if (h == "localhost") None else Some(h)
      logout(host)
    }
    case x => Pass
  }

  private def logout(host: Option[String]): ResponseFunction[Any] = {
    Ok ~> SetCookies(
      Cookie("username", "", host, maxAge = Some(-1), httpOnly = false),
      Cookie("login", "", host, maxAge = Some(-1), httpOnly = true)
    )
  }
}

object UserAndPassword {
  def unapply(req: HttpRequest[HttpServletRequest]) = {
    req match {
      case POST(_) & Params(p) => {
        import QParams._
        val exp = for {
          u <- lookup("username").is(required("Username is required"))
          p <- lookup("password").is(required("Password is required"))
        } yield {
          (u.get, p.get)
        }
        exp(p).fold(
          e => {
            println(e)
            None
          },
          login => Some(login)
        )
      }
    }
  }
}