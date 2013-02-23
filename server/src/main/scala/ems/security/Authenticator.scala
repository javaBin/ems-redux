package no.java.ems.security

import unfiltered.request.{QueryParams, BasicAuth, HttpRequest}
import unfiltered.response._
import unfiltered.Cycle

trait Authenticator[A,B] {
  type UserFunction = (User) => Cycle.Intent[A, B]

  object Authenticated {
    def apply(req: HttpRequest[A])(f: UserFunction): ResponseFunction[B] = {
      req match {
        case BasicAuth(u, p) => authenticate(u, p).fold(
          err => Unauthorized ~> WWWAuthenticate("Basic realm=\"ems\"") ~> ResponseString(err.getMessage),
          u => {
            val intent: Cycle.Intent[A, B] = f(u)
            if (intent.isDefinedAt(req)) intent(req) else Pass
          }
        )
        case QueryParams(p) => {
          if (p.contains("auth")) {
            Unauthorized ~> WWWAuthenticate("Basic realm=\"ems\"")
          } else f(Anonymous)(req)
        }
      }
    }

    def unapply(req: HttpRequest[A]): Option[(UserFunction) => ResponseFunction[B]] = Some(apply(req))
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