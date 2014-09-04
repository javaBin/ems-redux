package ems.storage

import com.mongodb.casbah.Imports._

object MongoSetting {
  def unapply(url: Option[String]): Option[MongoDB] = {
    url match {
      case Some(url) =>
        val uri = MongoClientURI(url)
        val client = MongoClient(uri)
        Some(client(uri.database.get))
      case None =>
        Some(MongoClient()("ems"))
    }
  }
}
