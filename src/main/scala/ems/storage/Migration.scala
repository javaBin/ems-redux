package ems.storage

import com.typesafe.scalalogging.LazyLogging
import ems.SqlConfig
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.util.jdbc.DriverDataSource

object Migration extends LazyLogging{

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
    logger.info(s"Migration scripts executed: $numMigrationExecuted")
  }

}
