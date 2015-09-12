package ems.storage

import ems.SqlConfig
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.util.jdbc.DriverDataSource

object Migration {

  def runMigration(cfg: SqlConfig) = {
    val fw = new Flyway()
    val ds = new DriverDataSource(
      Thread.currentThread.getContextClassLoader,
      cfg.driver,
      cfg.url,
      cfg.username,
      cfg.password
    )
    fw.setDataSource(ds)
    val numMigrationExecuted: Int = fw.migrate()
    println(s"Migration scripts executed: $numMigrationExecuted")
  }

}
