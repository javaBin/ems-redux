import sbt._
//import scala.slick.driver.PostgresDriver
//import scala.slick.jdbc.StaticQuery.interpolation

object Db {
  val rootUrl      = SettingKey[String]("db-root-url")
  val url          = SettingKey[String]("db-url")
  val dbName       = SettingKey[String]("db-schema")
  val user         = SettingKey[String]("db-user")
  val password     = SettingKey[String]("db-password")

  def settings:Seq[Setting[_]] = Seq(
    rootUrl := "jdbc:postgresql://127.0.0.1:5432",
    url <<= Def.setting{
      val databaseName = dbName.value.replaceAll("-", "_").replaceAll("/", "_").toLowerCase()
      ensureDatabase(rootUrl.value, databaseName, user.value, password.value)
      s"${rootUrl.value}/$databaseName"
    }
  )

  val DbLogger = new ProcessLogger {
    override def error(s: => String) = scala.Console.err.println(s"DB error: $s")
    override def buffer[T](f: => T) = f
    override def info(s: => String) = println(s"DB info: $s")
  }

  // synchronized da sbt laster prosjekter i parallell som fører til race condition i opprettelse av database
  // http://www.postgresql.org/message-id/4B994FC0.1030801@grammatech.com
  private def ensureDatabase(url: String, databaseName: String, user: String, password: String) = synchronized {
    /*PostgresDriver.simple.Database.forURL(url + "/postgres", user, password, driver = "org.postgresql.Driver").withSession {
      implicit s =>
        if (!sql"SELECT datname FROM pg_database WHERE datistemplate = false".as[String].list.contains(databaseName)) {
          println(s"Database $databaseName, does not exists. Trying to create it.")
          val s = Process("psql", Seq("-c", s"CREATE DATABASE $databaseName OWNER $user", "-U" , user, "postgres")).!(DbLogger)
          if (s != 0)
            sys.error("psql command failed with status " + s)
        }
    }*/
    ()
  }

}
