package ems.storage

import com.mongodb.casbah.{MongoDB, MongoConnection}

object MongoSetting {
  def unapply(url: Option[String]): Option[MongoDB] = {
    val MongoUrlWithPassword = """mongodb://(\w+):(\w+)@([\w|\.]+):(\d+)/(\w+)""".r
    val MongoUrl = """mongodb://([\w|\.]+):(\d+)/(\w+)""".r
    url match {
      case Some(MongoUrlWithPassword(u, p, host, port, dbName)) =>
        val db = MongoConnection(host, port.toInt)(dbName)
        db.authenticate(u,p)
        Some(db)
      case Some(MongoUrl(host, port, dbName)) => {
        Some(MongoConnection(host, port.toInt)(dbName))
      }
      case None =>
        Some(MongoConnection("localhost", 27017)("ems"))
    }
  }
}