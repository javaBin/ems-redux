package util

import ems.storage.{FilesystemBinaryStorage, MongoDBStorage, MongoSetting}
import ems.config.Config
import ems.model.{Session, Slot}
import org.joda.time.format.ISODateTimeFormat
import ems.security.Anonymous
import java.io.FileWriter

object SlotExport {
  val NoMillisFormatter = ISODateTimeFormat.basicDateTimeNoMillis().withZoneUTC()

  object storage extends MongoDBStorage {
    val MongoSetting(db) = None
    val binary = new FilesystemBinaryStorage(Config.server.binary)
  }

  def export(file: String = "/tmp/output.json") {

    val events = storage.getEvents().filterNot(_.slots.isEmpty)

    println(s"Found ${events.size} events")

    val slot = (s: Slot) => {
      """{
        |   "_id": "%s",
        |   "start": ISODate("%s"),
        |   "end":   ISODate("%s"),
        |   "last-modified": ISODate("%s")
        |}""".stripMargin
        .format(s.id.get, RFC3339.format(s.start), RFC3339.format(s.end), RFC3339.format(s.lastModified))
    }

    val session = (s: Session) => {
      """
        |{
        |  "_id": "%s",
        |  "slotId": "%s"
        |}""".stripMargin.format(s.id.get, s.slot.get.id.get)
    }

    val summary = events.map(e => {
      val unfiltered = storage.getSessions(e.id.get)(Anonymous)
      println(s"found ${unfiltered.size} sessions total in ${e.name}(${e.id.get})")
      val sessions = unfiltered.filter(_.slot.isDefined).map(session)
      println(s"found ${sessions.size} sessions filtered in ${e.name}(${e.id.get})")
      """{
        |  "_id" : "%s",
        |  "slots": %s,
        |  "sessions": %s
        |}""".stripMargin.format(e.id.get, e.slots.map(slot).mkString("[", ",", "]"), sessions.mkString("[", ",", "]"))
    }).mkString("[", ",", "]")


    val result = """
                   |conn = new Mongo();
                   |db = conn.getDB("ems");
                   |var events = %s;
                   |for (i in events) {
                   |  var event = events[i];
                   |  if (!event.sessions) {
                   |    event.sessions = [];
                   |  }
                   |  db.event.update({_id: event._id}, {$set: {slots: event.slots}});
                   |  var sessions = event.sessions;
                   |  for (y in sessions) {
                   |    var session = sessions[y];
                   |    db.session.update({eventId: event._id, _id: session._id}, {$set: {slotId: session.slotId}});
                   |  }
                   |}
                   |
                 """.stripMargin.format(summary)

    val writer = new FileWriter(file)
    writer.write(result)
    writer.close()
  }
}


