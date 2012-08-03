package no.java.ems.security

import unfiltered.request.{BasicAuth, HttpRequest}
import unfiltered.response._
import unfiltered.filter.Plan
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait Authenticator {
  type UserFunction = (Option[User]) => Plan.Intent

  object Authenticated {
    def apply(req: HttpRequest[HttpServletRequest])(f: UserFunction): ResponseFunction[HttpServletResponse] = {
      req match {
        case BasicAuth(u, p) => authenticate(u, p).fold(
          err => Unauthorized ~> WWWAuthenticate("Basic realm=\"ems\"") ~> ResponseString(err.getMessage),
          u => {
            val intent: Plan.Intent = f(Some(u))
            if (intent.isDefinedAt(req)) intent(req) else Pass
          }
        )
        case _ => f(None)(req)
      }
    }

    def unapply(req: HttpRequest[HttpServletRequest]): Option[(UserFunction) => ResponseFunction[HttpServletResponse]] = Some(apply(req))
  }

  def authenticate(username: String, password: String): Either[Exception, User]
}

case class User(name: String)