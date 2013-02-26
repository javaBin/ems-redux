package cake

import crypto.DES
import unfiltered.request._
import org.apache.commons.codec.binary.Base64
import javax.servlet.http.HttpServletRequest
import unfiltered.filter.Plan
import unfiltered.filter.request.ContextPath
import java.net.URI
import dispatch._
import unfiltered.response.{SetCookies, Ok}
import unfiltered.Cookie
import ems.Config


class LoginPlan extends Plan {
  def intent = {
    case req@ContextPath(_, Seg("login" :: Nil)) & UserExtractor(u) & HostPort(h, port) => {
      val host = if (h == "localhost") None else Some(h)
      val hasHost = Config.default.server.getHost != null
      val href = if (!hasHost) {
        URI.create(req.underlying.getRequestURL.toString).resolve(Config.default.server)
      }
      else {
        Config.default.server
      }
      val response = Http(url(href.toString + "?auth=true") as(u.username, u.password))()

      if (response.getStatusCode == 200) {
        Ok ~> SetCookies(
          Cookie("username", u.username, host, maxAge = Some(24 * 3600), httpOnly = false),
          Cookie("login", u.encrypted, host, maxAge = Some(24 * 3600), httpOnly = true)
        )
      }
      else {
        Ok ~> SetCookies.discarding("username", "login")
      }
    }
    case req@ContextPath(_, Seg("logout" :: Nil)) & HostPort(h, port) => {
      Ok ~> SetCookies.discarding("username", "login")
    }
  }
}


object EmsLoginHandler {
  def unapply[A](req: HttpRequest[A]): Option[User] = {
    req match {
      case Cookies(cookies) => {
        cookies("login").flatMap(c => User.create(c.value))
      }
    }
  }
}

case class User(username: String, password: String) {
  def encrypted = Base64.encodeBase64String(DES.encrypt(s"$username:$password".getBytes("UTF-8"), Config.default.password)).trim
}

object User {
  def create(input: String): Option[User] = {
    try {
      val decrypted = new String(DES.decrypt(Base64.decodeBase64(input), Config.default.password))
      decrypted.split(":") match {
        case Array(u, p) => Some(User(u, p))
        case _ => None
      }
    }
    catch {
      case e: Exception => None
    }
  }
}

object UserExtractor {
  def unapply(req: HttpRequest[HttpServletRequest]) = {
    req match {
      case POST(_) & Params(p) => {
        import QParams._
        val exp = for {
          u <- lookup("username").is(required("Username is required"))
          p <- lookup("password").is(required("Password is required"))
        } yield {
          User(u.get, p.get)
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