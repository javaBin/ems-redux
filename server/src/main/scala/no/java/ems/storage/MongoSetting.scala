package no.java.ems.storage

import com.mongodb.casbah.{MongoDB, MongoConnection}

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object MongoSetting {
  def unapply(url: Option[String]): Option[MongoDB] = {
    val regex = """mongodb://(\w+):(\w+)@([\w|\.]+):(\d+)/(\w+)""".r
    url match {
      case Some(regex(u, p, host, port, dbName)) =>
        val db = MongoConnection(host, port.toInt)(dbName)
        db.authenticate(u,p)
        Some(db)
      case None =>
        Some(MongoConnection("localhost", 27017)("ems"))
    }
  }
}