import com.mongodb.casbah.MongoConnection
import io.Source
import java.io.File
import no.java.ems.Event
import no.java.ems.storage.MongoDBStorage
import org.joda.time.format.ISODateTimeFormat

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Importer {

  object storage extends MongoDBStorage {
    def conn = MongoConnection()
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
