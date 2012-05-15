package no.java.ems.storage

import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import java.net.URI
import java.util.Locale
import no.java.ems._


/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait MongoDBStorage extends Storage {
  import MongoMapper._

  def conn: MongoConnection

  private val db = {
    val d = conn("ems")
    d("event").ensureIndex("name")
    d("session").ensureIndex("title")
    d
  }

  def getEvents() = db("event").find().map(toEvent).toList

  def getEvent(id: String) = db("event").findOneByID(new ObjectId(id)).map(toEvent)

  def getEventsByName(name: String) = db("event").find(MongoDBObject("name" -> name)).map(toEvent).toList

  def saveEvent(event: Event) = saveToMongo(event, db("event"))

  def getSessions(eventId: String) = db("session").find(MongoDBObject("eventId" -> new ObjectId(eventId))).map(toSession).toList

  def getSessionsByTitle(eventId: String, title: String) = db("session").find(
    MongoDBObject("eventId" -> new ObjectId(eventId), "title" -> title)
  ).map(toSession).toList

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> new ObjectId(id), "eventId" -> new ObjectId(eventId))
  ).map(toSession)

  def saveSession(session: Session) = saveToMongo(session, db("session"))

  def getContact(id: String) = db("contact").findOneByID(new ObjectId(id)).map(toContact(_, this))

  def getContacts() = db("contact").find().map(toContact(_, this)).toList

  def saveContact(contact: Contact) = saveToMongo(contact, db("contact"))


  def getAttachment(id: String) = {
    val fs = GridFS(db)
    fs.findOne(new ObjectId(id)).map(GridFileAttachment)
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
    file.save
    GridFileAttachment(file)
  }

  def removeAttachment(id: String) = {
    val fs = GridFS(db)
    fs.remove(new ObjectId(id))
  }

  def shutdown() {
    conn.close()
  }

  private def saveToMongo[A <: Entity](entity: A, coll: MongoCollection): A#T = {
    val stored = withId(entity)
    val toSave = toMongoDBObject(stored)
    if (entity.id.isDefined) {
      coll.update(MongoDBObject("_id" -> new ObjectId(entity.id.get)), toSave)
    }
    else {
      coll.insert(toSave, WriteConcern.Normal)
    }
    stored
  }

  private def withId[Y <: Entity](entity: Y): Y#T = {
    val id = entity.id.getOrElse(new ObjectId().toString)
    entity.withId(id)
  }
}

private [storage] object MongoMapper {
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
      m.getAsOrElse("last-modified", new DateTime())
    )
  }

  val toSession: (DBObject) => Session = (dbo) => {
    val m = wrapDBObj(dbo)
    val abs = m.getAs[DBObject]("abstract").map(toAbstract).getOrElse(new Abstract("No Title"))

    Session(
      m.get("_id").map(_.toString),
      m.get("eventId").map(_.toString).getOrElse(throw new IllegalArgumentException("No eventId")),
      m.get("duration").map(d => org.joda.time.Duration.parse(d.toString)),
      abs,
      m.getAs[String]("state").map(State(_)).getOrElse(State.Pending),
      m.getAs[Boolean]("published").getOrElse(false),
      Nil,
      m.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).map(t => Tag(t.toString)).toSet[Tag],
      m.getAsOrElse[MongoDBList]("keywords", MongoDBList.empty).map(k => Keyword(k.toString)).toSet[Keyword],
      m.getAsOrElse("last-modified", new DateTime())
    )
  }

  val toAbstract: (DBObject) => Abstract = (dbo) => {
    val m = wrapDBObj(dbo)
    val title = m.getAsOrElse("title", "No Title")
    val format = m.getAs[String]("format").map(Format(_)).getOrElse(Format.Presentation)
    val level = m.getAs[String]("level").map(Level(_)).getOrElse(Level.Beginner)
    val lead = m.getAs[String]("lead")
    val body = m.getAs[String]("body")
    val language = m.getAs[String]("language").map(l => new Locale(l)).getOrElse(new Locale("no"))
    val speakers = m.getAsOrElse[Seq[_]]("speakers", Seq()).map{case x:DBObject => x}.map(toSpeaker)
    Abstract(title, lead, body, language, level, format, Vector() ++ speakers)
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
    val foreign = m.getAsOrElse("foreign", false)
    val emails = m.getAs[Seq[_]]("emails").getOrElse(Nil).map(e => Email(e.toString)).toList
    val image = m.get("image").flatMap(i => storage.getAttachment(i.toString))
    val bio = m.getAs[String]("bio")
    val lm = m.getAsOrElse("last-modified", new DateTime())
    Contact(id, name, foreign, bio, emails, image, lm)
  }

  val toSpeaker: (DBObject) => Speaker = (dbo) => throw new UnsupportedOperationException()

  def toMongoDBObject[A <: Entity#T](entity: A): DBObject = entity match {
    case s: Session => toMongoDBObject(s)
    case s: Contact => toMongoDBObject(s)
    case s: Event => toMongoDBObject(s)
    case _ => throw new UnsupportedOperationException("Not supported")
  }

  private def toMongoDBObject(event: Event): DBObject = {
    MongoDBObject("_id" -> event.id.map(i => new ObjectId(i)).getOrElse(new ObjectId()), "name" -> event.name, "start" -> event.start, "end" -> event.end, "last-modified" -> event.lastModified)
  }

  private def toMongoDBObject(session: Session): DBObject = {
    MongoDBObject(
      "_id" -> session.id.map(i => new ObjectId(i)).getOrElse(new ObjectId()),
      "eventId" -> new ObjectId(session.eventId),
      "abstract" -> toMongoDBObject(session.abs),
      "published" -> session.published,
      "tags" -> session.tags.map(_.name),
      "keywords" -> session.keywords.map(_.name),
      "duration" -> session.duration.map(_.toString).orNull,
      "state" -> session.state.name,
      "attachments" -> session.attachments.map(toMongoDBObject),
      "last-modified" -> session.lastModified
    )
  }

  private def toMongoDBObject(contact: Contact): DBObject = {
    val obj = MongoDBObject(
      "_id" -> contact.id.map(i => new ObjectId(i)).getOrElse(new ObjectId()),
      "name" -> contact.name,
      "foreign" -> contact.foreign,
      "emails" -> contact.emails.map(_.address),
      "last-modified" -> contact.lastModified
    )
    obj.putAll(contact.bio.map(b => "bio" -> b).toMap)
    obj
  }

  private def toMongoDBObject(att: URIAttachment): DBObject = {
    val obj = MongoDBObject(
      "href" -> att.href.toString,
      "name" -> att.name,
      "mime-type" -> att.mediaType.map(_.toString),
      "size" -> att.size
    )
    obj
  }

  private def toMongoDBObject(abs: Abstract): DBObject = {
    val obj = MongoDBObject(
      "title" -> abs.title,
      "format" -> abs.format.name,
      "level" -> abs.level.name,
      "language" -> abs.language.getLanguage,
      "speakers" -> abs.speakers.map(toMongoDBObject)
    )
    obj.putAll(abs.body.map(b => "body" -> b).toMap)
    obj.putAll(abs.lead.map(b => "lead" -> b).toMap)
    obj
  }

  private def toMongoDBObject(speaker: Speaker): DBObject = {
    val obj = MongoDBObject("_id" -> new ObjectId(speaker.contactId), "name" -> speaker.name)
    speaker.image.map(a => "photo" -> new ObjectId(a.id.get))
    obj.putAll(speaker.bio.map(b => "bio" -> b).toMap)
    obj
  }
}
