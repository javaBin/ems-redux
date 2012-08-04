package no.java.ems.security

import javax.security.auth.login.{LoginException, LoginContext}
import javax.security.auth.callback._
import scala.Left
import scala.Right

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object JAASAuthenticator extends Authenticator {
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
      Right(User(username))
    }
    catch {
      case e: LoginException => Left(e)
      case e: UnsupportedCallbackException => Left(e)
    }
  }

}

