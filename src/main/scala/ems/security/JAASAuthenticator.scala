package ems.security

import javax.security.auth.login.{LoginException, LoginContext}
import javax.security.auth.callback._

import scalaz.\/

class JAASAuthenticator[A, B] extends Authenticator[A, B] {
  def authenticate(username: String, password: String) = {
    val context = new LoginContext("ems", new CallbackHandler {
      def handle(callbacks: Array[Callback]) {
        callbacks.foreach {
          case n: NameCallback => n.setName(username)
          case n: PasswordCallback => n.setPassword(password.toCharArray)
          case cb => throw new UnsupportedCallbackException(cb, "Uexpected callback found")
        }
      }
    })
    try {
      context.login()
      \/.right(AuthenticatedUser(username))
    }
    catch {
      case e: LoginException => \/.left(e)
      case e: UnsupportedCallbackException => \/.left(e)
    }
  }

}

