package ems.security

import java.io.{BufferedInputStream, File, FileInputStream}
import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
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

object PropertyFileAuthenticator extends LazyLogging {
  def apply[A, B](file: File): PropertyFileAuthenticator[A, B] = {
    logger.info("Loaded passwords from " + file)
    val props = new Properties()
    if (file.exists()) {
      IO.use(new BufferedInputStream(new FileInputStream(file))) { is => props.load(is) }
    }

    val map = props.asScala.toMap
    logger.info("Found users {}", map.keys.mkString(", "))
    new PropertyFileAuthenticator[A, B](map)
  }


  /*def main(args: Array[String]): Unit = {
    val username = args(0)
    val password = args(1)
    println(s"$username=${BCrypt.hashpw(password, BCrypt.gensalt())}")
  }*/
}
