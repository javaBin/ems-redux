package ems

import crypto.DES
import unfiltered.request._
import org.apache.commons.codec.binary.Base64

object EmsLoginHandler {
  def unapply[A](req: HttpRequest[A]): Option[User] = {
    req match {
      case Cookies(cookies) => {
        cookies("login").flatMap(c => User.create(c.value))
      }
    }
  }
}

case class User(username: String, password: String)

object User {
  def create(input: String): Option[User] = {
    val decrypted = new String(DES.decrypt(Base64.decodeBase64(input), EmsConfig.password))
    decrypted.split(":") match {
      case Array(u, p) => Some(User(u, p))
      case _ => None
    }
  }
}