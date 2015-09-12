package ems

import java.io.File

case class Config(binary: File, sql: SqlConfig, cache: CacheConfig)

case class CacheConfig(events: Int = 30, sessions: Int = 30)

case class SqlConfig(host: String = "localhost",
                     port: Int = 5432,
                     database: String = "ems",
                     username: String = "ems",
                     password: String = "ems") {
  val url = s"jdbc:postgresql://$host:$port/$database"
  val driver = "org.postgresql.Driver"

  //def transactor: Transactor[Task] = DriverManagerTransactor(driver, url, username, password)
}

object Config {

}
