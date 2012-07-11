package no.java.ems.storage

import com.mongodb.casbah.gridfs.{GridFSDBFile, GridFS}
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import java.net.URI
import java.util.Locale
import no.java.ems._
import java.util
import model._


/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait MongoDBStorage extends Storage {

  import MongoMapper._

  def db: MongoDB

  def getEvents() = db("event").find().map(toEvent).toList

  def getEvent(id: String) = db("event").findOneByID(id).map(toEvent)

  def getEventsByName(name: String) = db("event").find(MongoDBObject("name" -> name)).map(toEvent).toList

  def saveEvent(event: Event) = saveToMongo(event, db("event"))

  def getSessions(eventId: String) = db("session").find(MongoDBObject("eventId" -> eventId)).map(toSession(_, this)).toList

  def getSessionsByTitle(eventId: String, title: String) = db("session").find(
    MongoDBObject("eventId" -> eventId, "title" -> title)
  ).map(toSession(_, this)).toList

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> id, "eventId" -> eventId)
  ).map(toSession(_, this))

  def saveSession(session: Session) = saveToMongo(session, db("session"))

  def getContact(id: String) = db("contact").findOneByID(id).map(toContact(_, this))

  def getContacts() = db("contact").find().map(toContact(_, this)).toList

  def saveContact(contact: Contact) = saveToMongo(contact, db("contact"))

  def getAttachment(id: String): Option[Attachment with Entity] = {
    val fs = GridFS(db)
    fs.findOne(id).map(GridFileAttachment)
  }

  def importEntity[A <: Entity](entity: A): A#T = {
    entity match {
      case e: Contact => saveToMongo[A](entity, db("contact"), true)
      case e: Event => saveToMongo[A](entity, db("event"), true)
      case e: Session => saveToMongo[A](entity, db("event"), true)
      case _ => throw new IllegalArgumentException("Unknown entity")
    }
  }

  def saveAttachment(att: Attachment) = {
    val fs = GridFS(db)
    val file = att match {
      case GridFileAttachment(f) => f
      case a => {
        val f = fs.createFile(getStream(a), a.name)
        a.mediaType.foreach(mt => f.contentType = mt.toString)
        f
      }
    }
    file.metaData.put("last-modified", new DateTime())
    file.save()
    getAttachment(file.id.toString).getOrElse(throw new IllegalArgumentException("Failed to save"))
  }

  def removeAttachment(id: String) {
    val fs = GridFS(db)
    fs.remove(id)
  }

  def shutdown() {
    db.underlying.getMongo.close()
  }

  private def saveToMongo[A <: Entity](entity: A, coll: MongoCollection, fromImport: Boolean = false): A#T = {
    val stored = withId(entity)
    val toSave = toMongoDBObject(stored)

    if (entity.id.isDefined && !fromImport) {
      coll.update(MongoDBObject("_id" -> entity.id.get), toSave)
    }
    else {
      coll.insert(toSave, WriteConcern.Normal)
    }
    stored
  }

  private def withId[Y <: Entity](entity: Y): Y#T = {
    val id = entity.id.getOrElse(util.UUID.randomUUID().toString)
    entity.withId(id)
  }
}

private[storage] object MongoMapper {

  import com.mongodb.casbah.Imports._
  import com.mongodb.casbah.commons.conversions.scala._

  RegisterJodaTimeConversionHelpers()

  val toEvent: (DBObject) => Event = (dbo) => {
    val m = wrapDBObj(dbo)
    Event(
      m.get("_id").map(_.toString),
      m.getAsOrElse("name", "No Name"),
      m.getAsOrElse("start", new DateTime()),
      m.getAsOrElse("end", new DateTime()),
      m.getAsOrElse("venue", "Unknown"),
      m.getAsOrElse[Seq[_]]("rooms", Seq()).map(o => {
        val w = wrapDBObj(o.asInstanceOf[DBObject])
        Room(w.getAs[String]("_id"), w.as[String]("name"))
      }),
      m.getAsOrElse[Seq[_]]("slots", Seq()).map(o => {
        val w = wrapDBObj(o.asInstanceOf[DBObject])
        Slot(w.getAs[String]("_id"), w.getAsOrElse("start", new DateTime(0L)), w.getAsOrElse("end", new DateTime(0L)))
      }),
      m.getAsOrElse("last-modified", new DateTime())
    )
  }

  def toSession(dbo: DBObject, storage: MongoDBStorage) = {
    val m = wrapDBObj(dbo)
    val abs = m.getAs[DBObject]("abstract").map(toAbstract(_, storage)).getOrElse(new Abstract("No Title"))

    Session(
      m.get("_id").map(_.toString),
      m.get("eventId").map(_.toString).getOrElse(throw new IllegalArgumentException("No eventId")),
      m.get("slotId").map(_.toString),
      m.get("roomId").map(_.toString),
      abs,
      m.getAs[String]("state").map(State(_)).getOrElse(State.Pending),
      m.getAs[Boolean]("published").getOrElse(false),
      Nil,
      m.getAsOrElse[Seq[_]]("tags", Seq.empty).map(t => Tag(t.toString)).toSet[Tag],
      m.getAsOrElse[Seq[_]]("keywords", Seq.empty).map(k => Keyword(k.toString)).toSet[Keyword],
      m.getAsOrElse("last-modified", new DateTime())
    )
  }

  private def toAbstract(dbo: DBObject, storage: MongoDBStorage) = {
    val m = wrapDBObj(dbo)
    val format = m.getAs[String]("format").map(Format(_)).getOrElse(Format.Presentation)
    val level = m.getAs[String]("level").map(Level(_)).getOrElse(Level.Beginner)
    val speakers = m.getAsOrElse[Seq[_]]("speakers", Seq()).map {
      case x: DBObject => x
    }.map(toSpeaker(_, storage))
    Abstract(
      m.getAsOrElse("title", "No Title"),
      m.getAs[String]("summary"),
      m.getAs[String]("body"),
      m.getAs[String]("audience"),
      m.getAs[String]("outline"),
      m.getAs[String]("language").map(l => new Locale(l)).getOrElse(new Locale("no")),
      level,
      format,
      speakers
    )
  }

  private def toSpeaker(dbo: DBObject, storage: MongoDBStorage) = {
    val m = wrapDBObj(dbo)
    Speaker(
      m.get("_id").map(_.toString).get,
      m.as[String]("name"),
      m.getAs[String]("bio"),
      m.get("photo").flatMap(i => storage.getAttachment(i.toString))
    )
  }

  val toAttachment: (DBObject) => URIAttachment = (dbo) => {
    val m = wrapDBObj(dbo)
    val href = URI.create(m.getAs[String]("href").get)
    val mt = m.getAs[String]("mime-type").flatMap(MIMEType(_))
    val name = m.getAs[String]("name").get
    val size = m.getAs[Long]("size")
    URIAttachment(href, name, size, mt)
  }

  def toContact(dbo: DBObject, storage: MongoDBStorage) = {
    val m = wrapDBObj(dbo)
    val id = m.get("_id").map(_.toString)
    val name = m.as[String]("name")
    val locale = m.getAs[String]("locale").map(l => new Locale(l)).getOrElse(new Locale("no"))
    val emails = m.getAs[Seq[_]]("emails").getOrElse(Nil).map(e => Email(e.toString)).toList
    val photo = m.get("photo").flatMap(i => storage.getAttachment(i.toString))
    val bio = m.getAs[String]("bio")
    val lm = m.getAsOrElse("last-modified", new DateTime())
    Contact(id, name, bio, emails, locale, photo, lm)
  }

  def toMongoDBObject[A <: Entity#T](entity: A): DBObject = entity match {
    case s: Session => toMongoDBObject(s)
    case s: Contact => toMongoDBObject(s)
    case s: Event => toMongoDBObject(s)
    case _ => throw new UnsupportedOperationException("Not supported")
  }

  private def toMongoDBObject(event: Event): DBObject = {
    MongoDBObject(
      "_id" -> event.id.getOrElse(util.UUID.randomUUID().toString),
      "name" -> event.name,
      "start" -> event.start,
      "end" -> event.end,
      "venue" -> event.venue,
      "rooms" -> event.rooms.map(r => MongoDBObject(
        "_id" -> r.id.getOrElse(util.UUID.randomUUID().toString),
        "name" -> r.name
      )),
      "slots" -> event.slots.map(ts => MongoDBObject(
        "_id" -> ts.id.getOrElse(util.UUID.randomUUID().toString),
        "start" -> ts.start,
        "end" -> ts.end
      )),
      "last-modified" -> event.lastModified
    )
  }

  private def toMongoDBObject(session: Session): DBObject = {
    MongoDBObject(
      "_id" -> session.id.getOrElse(util.UUID.randomUUID().toString),
      "eventId" -> session.eventId,
      "abstract" -> toMongoDBObject(session.abs),
      "published" -> session.published,
      "tags" -> session.tags.map(_.name),
      "keywords" -> session.keywords.map(_.name),
      "state" -> session.state.name,
      "attachments" -> session.attachments.map(toMongoDBObject),
      "roomId" -> session.roomId,
      "slotId" -> session.slotId,
      "last-modified" -> session.lastModified
    )
  }

  private def toMongoDBObject(contact: Contact): DBObject = {
    val obj = MongoDBObject(
      "_id" -> contact.id.getOrElse(util.UUID.randomUUID().toString),
      "name" -> contact.name,
      "bio" -> contact.bio,
      "photo" -> contact.photo.map(_.id),
      "locale" -> contact.locale.getLanguage,
      "emails" -> contact.emails.map(_.address),
      "last-modified" -> contact.lastModified
    )
    obj
  }

  private def toMongoDBObject(att: URIAttachment): DBObject = {
    MongoDBObject(
      "href" -> att.href.toString,
      "name" -> att.name,
      "mime-type" -> att.mediaType.map(_.toString),
      "size" -> att.size
    )
  }

  private def toMongoDBObject(abs: Abstract): DBObject = {
    MongoDBObject(
      "title" -> abs.title,
      "format" -> abs.format.name,
      "level" -> abs.level.name,
      "language" -> abs.language.getLanguage,
      "speakers" -> abs.speakers.map(toMongoDBObject)
    )
  }

  private def toMongoDBObject(speaker: Speaker): DBObject = {
    MongoDBObject(
      "_id" -> speaker.contactId,
      "name" -> speaker.name,
      "bio" -> speaker.bio,
      "photo" -> speaker.photo.map(_.id)
    )
  }
}

case class GridFileAttachment(file: GridFSDBFile) extends Attachment with Entity {
  type T = GridFileAttachment

  def data = file.inputStream

  def name = file.filename.get

  def size = Some(file.size)

  def mediaType = file.contentType.flatMap(MIMEType(_))

  def lastModified = file.metaData.getAsOrElse[DateTime]("last-modified", new DateTime())

  def id = file.id match {
    case null => None
    case i => Some(i.toString)
  }

  def withId(id: String) = {
    file.put("_id", id)
    this
  }
}
