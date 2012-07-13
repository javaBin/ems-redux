package resource

import java.lang.String
import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import org.joda.time.DateTime

sealed trait Resource {
  def prefix: String

  def path: String

  def inputStream: () => Either[String, InputStream]

  def file: () => Option[File]

  def exists: Boolean

  def lastModified: DateTime
}

object Resource {

  import ResourceTypes._

  def apply(location: String): Resource = {
    location match {
      case ClassPathPrefixed(path) => new ClassPathResource(path)
      case FilePrefixed(path) => new FileResource(path)
      case _ => new UnknownResourceType(location)
    }
  }
}

sealed trait KnownResource extends Resource

final case class UnknownResourceType(path: String) extends Resource {
  def prefix = ""

  def exists = false

  def inputStream = () => Left("Cannot not get stream for unknown resource type")

  def file = () => None

  def lastModified = new DateTime
}

final case class ByteArrayResource(path: String, bytes: Array[Byte]) extends KnownResource {
  def prefix = "bytes:"

  def inputStream = () => Right(new ByteArrayInputStream(bytes))

  def file = () => None

  def exists = true

  def lastModified = new DateTime
}

final case class ClassPathResource(path: String) extends KnownResource {

  def prefix = "classpath:"

  def exists = load != null


  def file = () => if (exists) Some(new File(getClass.getResource(if (path.startsWith("/")) path else "/" + path).getFile)) else None

  def inputStream = () => {
    try {
      Right(load)
    } catch {
      case e: Throwable => Left(e.getMessage)
    }
  }

  private def load = getClass.getResourceAsStream(if (path.startsWith("/")) path else "/" + path)


  def lastModified = file().map(x => new DateTime(x.lastModified())).getOrElse(new DateTime)

  override def toString = """[ClassPathResource "%S"]""".formatted(path)
}

final case class FileResource(path: String) extends KnownResource {

  def prefix = "file:"

  import java.io.File

  def file = () => Option(new File(path)).filter(_.exists())

  def exists = {
    val file = new File(path)
    file.exists() && file.canRead
  }

  def inputStream = () => {
    try {
      Right(new FileInputStream(path))
    } catch {
      case e: Throwable => Left(e.getMessage)
    }
  }

  def lastModified = file().map(x => new DateTime(x.lastModified())).getOrElse(new DateTime)

  override def toString = """[FileResource "%S"]""".formatted(path)
}

object ResourceTypes {

  object ClassPathPrefixed {
    val prefix = "classpath:"

    def unapply(location: String): Option[String] = if (location.startsWith(prefix)) Some(location.substring(prefix.length())) else None
  }

  object FilePrefixed {
    val prefix = "file:"

    def unapply(location: String): Option[String] = if (location.startsWith(prefix)) Some(location.substring(prefix.length())) else None
  }

}
