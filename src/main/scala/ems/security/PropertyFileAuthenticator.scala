package ems.security

import java.io.{BufferedInputStream, FileInputStream, File}
import java.util.Properties
import org.mindrot.jbcrypt.BCrypt

import collection.JavaConverters._

import unfiltered.util.IO

import scalaz.\/

class PropertyFileAuthenticator[A, B] private(properties: Map[String, String]) extends Authenticator[A, B]{
  override def authenticate(username: String, password: String): Exception \/ User = {
    if (properties.get(username).exists(p => BCrypt.checkpw(password, p))) \/.right(AuthenticatedUser(username))
    else \/.left(new SecurityException("Username/Password did not match"))
  }
}

object PropertyFileAuthenticator {
  def apply[A, B](file: File): PropertyFileAuthenticator[A, B] = {
    val props = new Properties()
    if (file.exists()) {
      IO.use(new BufferedInputStream(new FileInputStream(file))) { is => props.load(is) }
    }
    new PropertyFileAuthenticator[A, B](props.asScala.toMap)
  }


  /*def main(args: Array[String]): Unit = {
    val username = args(0)
    val password = args(1)
    println(s"$username=${BCrypt.hashpw(password, BCrypt.gensalt())}")
  }*/
}
