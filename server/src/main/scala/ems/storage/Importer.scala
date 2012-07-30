package no.java.ems.storage

import java.io.{FileInputStream, BufferedReader, FileReader, File}
import java.util.Locale
import javax.activation.FileTypeMap
import net.liftweb.json.{DefaultFormats, JsonParser}
import no.java.ems.{URIAttachment, StreamingAttachment, MIMEType, Attachment}
import no.java.ems.model._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import scala.util.Properties
import java.net.URI


object ImportMain extends App {

  Importer.execute(new File(args.headOption.getOrElse("/tmp/ems")))
}

object Importer {
  object storage extends MongoDBStorage {
    val MongoSetting(db) = Properties.envOrNone("MONGOLAB_URI")
  }

  implicit val Formats = DefaultFormats

  val isoDF = ISODateTimeFormat.basicDateTimeNoMillis


  def execute(baseDir: File = new File("/tmp/ems")) {
    contacts(new File(baseDir, "contacts.json")).foreach(c => {
      val written = storage.importEntity[Contact](c)
      println("Wrote " + c.name + " to DB with id " + written.id.get)
    })
    events(new File(baseDir, "events.json")).foreach(e => {
      val written = storage.importEntity[Event](e)
      println("Wrote " + e.name + " to DB with id " + written.id.get)
      val id = written.id.getOrElse("")
      val eventDir = new File(baseDir, id)
      if (new File(eventDir, "sessions.json").exists()) {
        sessions(new File(eventDir, "sessions.json")).foreach(s => {
          val written = storage.importEntity[Session](s)
          println("Wrote " + s.abs.title + " to DB with id " + written.id.get)
        })
      }
    })
    storage.shutdown()
  }

  def contacts(file: File) = {
    val parsed = JsonParser.parse(new BufferedReader(new FileReader(file)))
    (parsed \ "contacts").children.map(c =>
      Contact(
        (c \ "id").extractOpt[String],
        (c \ "name").extract[String],
        (c \ "bio").extractOpt[String],
        (c \ "emails").extract[List[String]].map(Email(_)),
        (c \ "locale").extractOpt[String].map(l => new Locale(l)).getOrElse(new Locale("no")),
        (c \ "photo").extractOpt[String].map(f => {
          val file = new File(f)
          storage.saveAttachment(
            StreamingAttachment(
              file.getName,
              Some(file.length()),
              MIMEType(FileTypeMap.getDefaultFileTypeMap.getContentType(file.getName)),
              new FileInputStream(file)
            ))
        })
      )
    )
  }

  def events(file: File) = {
    val parsed = JsonParser.parse(new BufferedReader(new FileReader(file)))
    (parsed \ "events").children.map(c =>
      Event(
        (c \ "id").extractOpt[String],
        (c \ "name").extract[String],
        (c \ "start").extractOpt[String].map(isoDF.parseDateTime(_)).getOrElse(new DateTime(0L)),
        (c \ "end").extractOpt[String].map(isoDF.parseDateTime(_)).getOrElse(new DateTime(1L)),
        (c \ "venue").extract[String],
        (c \ "rooms").children.map(o => {
          Room(
            None,
            (o \ "name").extract[String]
          )
        }),
        (c \ "timeslots").children.map(o => {
          Slot(
            None,
            (o \ "start").extractOpt[String].map(isoDF.parseDateTime(_)).getOrElse(new DateTime(0L)),
            (o \ "end").extractOpt[String].map(isoDF.parseDateTime(_)).getOrElse(new DateTime(1L))
          )
        })
      )
    )
  }

  def sessions(file: File) = {
    val parsed = JsonParser.parse(new BufferedReader(new FileReader(file)))
    (parsed \ "sessions").children.map(c =>
      Session(
        (c \ "id").extractOpt[String],
        (c \ "eventId").extract[String],
        (c \ "room").extractOpt[String],
        (c \ "slot").extractOpt[String],
        Abstract(
          Option((c \ "title").extract[String]).getOrElse("No title"),
          (c \ "summary").extractOpt[String],
          (c \ "body").extractOpt[String],
          (c \ "audience").extractOpt[String],
          (c \ "outline").extractOpt[String],
          (c \ "equipment").extractOpt[String],
          (c \ "locale").extractOpt[String].map(l => new Locale(l)).getOrElse(new Locale("no")),
          (c \ "level").extractOpt[String].map(Level(_)).getOrElse(Level.Beginner),
          (c \ "format").extractOpt[String].map(Format(_)).getOrElse(Format.Presentation),
          (c \ "speaker").children.map(s =>
            Speaker(
              (s \ "id").extract[String],
              (s \ "name").extract[String],
              (s \ "bio").extractOpt[String],
              (c \ "photo").extractOpt[String].map(f => {
                val file = new File(f)
                storage.saveAttachment(StreamingAttachment(file))
              })
            ))
          ),
        (c \ "state").extractOpt[String].map(State(_)).getOrElse(State.Pending),
        (c \ "published").extractOrElse(false),
        (c \ "attachments").children.map(f => {
          val file = new File(f.values.toString)
          val att: Attachment with Entity = storage.saveAttachment(StreamingAttachment(file))
          URIAttachment(
            URI.create(att.id.get),
            att.name,
            att.size,
            att.mediaType
          )
        }),
        (c \ "tags").extractOpt[String].map(_.split(",").map(Tag(_)).toSet[Tag]).getOrElse(Set.empty),
        (c \ "keywords").extractOpt[String].map(_.split(",").map(Keyword(_)).toSet[Keyword]).getOrElse(Set.empty)
      )
    )
  }
}