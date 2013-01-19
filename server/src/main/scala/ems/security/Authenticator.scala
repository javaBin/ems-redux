package no.java.ems.security

import unfiltered.request.{BasicAuth, HttpRequest}
import unfiltered.response._
import unfiltered.filter.Plan
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait Authenticator {
  type UserFunction = (User) => Plan.Intent

  object Authenticated {
    def apply(req: HttpRequest[HttpServletRequest])(f: UserFunction): ResponseFunction[HttpServletResponse] = {
      req match {
        case BasicAuth(u, p) => authenticate(u, p).fold(
          err => Unauthorized ~> WWWAuthenticate("Basic realm=\"ems\"") ~> ResponseString(err.getMessage),
          u => {
            val intent: Plan.Intent = f(u)
            if (intent.isDefinedAt(req)) intent(req) else Pass
          }
        )
        case _ => f(Anonymous)(req)
      }
    }

    def unapply(req: HttpRequest[HttpServletRequest]): Option[(UserFunction) => ResponseFunction[HttpServletResponse]] = Some(apply(req))
  }

  def authenticate(username: String, password: String): Either[Exception, User]
}

sealed trait User {
  def name: String

  def authenticated: Boolean
}

case class AuthenticatedUser(name: String) extends User {
  val authenticated = true
}

object Anonymous extends User {
  val name = "Anonymous"
  val authenticated = false
}