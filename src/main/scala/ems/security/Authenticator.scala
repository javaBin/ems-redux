package ems.security

import unfiltered.request.{QueryParams, BasicAuth, HttpRequest}
import unfiltered.response._
import unfiltered.Async

import scalaz.\/

trait Authenticator[A,B] {
  type UserFunction = (User) => Async.Intent[A, B]

  object Authenticated {
    def apply(req: HttpRequest[A] with Async.Responder[B])(f: UserFunction): Any = {
      req match {
        case BasicAuth(u, p) => authenticate(u, p).fold(
          err => req.respond(Unauthorized ~> WWWAuthenticate("Basic realm=\"ems\"") ~> ResponseString(err.getMessage)),
          u => {
            val intent: Async.Intent[A, B] = f(u)
            if (intent.isDefinedAt(req)) intent(req) else req.respond(Pass)
          }
        )
        case QueryParams(p) => {
          if (p.contains("auth")) {
            Unauthorized ~> WWWAuthenticate("Basic realm=\"ems\"")
          } else f(Anonymous)(req)
        }
      }
    }

    def unapply(req: HttpRequest[A] with Async.Responder[B]): Option[(UserFunction) => Any] = Some(apply(req))
  }

  def authenticate(username: String, password: String): Exception \/ User
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
