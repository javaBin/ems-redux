import io.Source
import java.io.File
import no.java.ems.Event
import no.java.ems.storage.{MongoSetting, MongoDBStorage}
import org.joda.time.format.ISODateTimeFormat
import util.Properties

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Importer {

  object storage extends MongoDBStorage {
    val MongoSetting(db) = Properties.envOrNone("MONGOLAB_URI")
  }
  val isoDF = ISODateTimeFormat.basicDateTimeNoMillis

  def importEvent(file: File) {

    val lines = Source.fromFile(file).getLines()
    lines.foreach(line => {
      val fields = line.split("|")
      Event(None, fields(0), isoDF.parseDateTime(fields(1)), isoDF.parseDateTime(fields(2)))
    })
  }

}
