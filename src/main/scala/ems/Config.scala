package ems

import java.io.File
import java.util.Properties
import javax.sql.DataSource

import com.zaxxer.hikari.{HikariDataSource, HikariConfig}

case class Config(binary: File, sql: SqlConfig, cache: CacheConfig)

case class CacheConfig(events: Int = 30, sessions: Int = 30)

case class SqlConfig(host: String = "localhost",
                     port: Int = 5432,
                     database: String = "ems",
                     username: String = "ems",
                     password: String = "ems") {
  val url = s"jdbc:postgresql://$host:$port/$database"
  val driver = "org.postgresql.Driver"

  def dataSource(): DataSource = {
    val props = new Properties()
    props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
    props.setProperty("dataSource.user", username)
    props.setProperty("dataSource.password", password)
    props.setProperty("dataSource.databaseName", database)

    val config = new HikariConfig(props)
    new HikariDataSource(config)
  }


}
