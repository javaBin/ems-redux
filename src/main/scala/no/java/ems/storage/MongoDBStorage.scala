package no.java.ems.storage

import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import no.java.ems._


/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait MongoDBStorage extends Storage {
  import MongoMapper._

  def conn: MongoConnection

  //def conn = MongoConnection(host, port)
  private val db = conn("ems")

  def getEvents() = db("event").find().map(toEvent).toList

  def getEvent(id: String) = db("event").findOneByID(new ObjectId(id)).map(toEvent)

  def saveEvent(event: Event) = saveToMongo(event, db("event"))

  def getSessions(eventId: String) = db("session").find(MongoDBObject("eventId" -> new ObjectId(eventId))).map(toSession).toList

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> new ObjectId(id), "eventId" -> new ObjectId(eventId))
  ).map(toSession)

  def saveSession(session: Session) = saveToMongo(session, db("session"))

  def getContact(id: String) = db("contact").findOneByID(new ObjectId(id)).map(toContact)

  def getContacts() = db("contact").find().map(toContact).toList

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
        val f = fs.createFile(a.data, a.name)
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
  import com.mongodb.casbah.commons.conversions.scala._
  RegisterJodaTimeConversionHelpers()

  val toEvent: (DBObject) => Event = (dbo) => {
    val m = wrapDBObj(dbo)
    Event(
      m.get("_id").map(_.toString),
      m.getAsOrElse("title", "No Title"),
      m.getAsOrElse("start", new DateTime()),
      m.getAsOrElse("end", new DateTime()),
      m.getAsOrElse("last-modified", new DateTime())
    )
  }

  val toSession: (DBObject) => Session = (dbo) => {
    val m = wrapDBObj(dbo)
    val abs = m.get("abstract").map{case x: DBObject => x}.map(toAbstract).getOrElse(new Abstract("No Title"))

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

  val toAbstract: (DBObject) => Abstract = (m) => throw new UnsupportedOperationException()

  val toContact: (DBObject) => Contact = (m) => throw new UnsupportedOperationException()

  implicit def toMongoDBObject(event: Event): DBObject = {
    MongoDBObject("_id" -> event.id.map(i => new ObjectId(i)).getOrElse(new ObjectId()), "title" -> event.title, "start" -> event.start, "end" -> event.end, "last-modified" -> event.lastModified)
  }

  implicit def toMongoDBObject[A <: Entity#T](entity: A): DBObject = entity match {
    case s: Session => toMongoDBObject(s)
    case s: Contact => toMongoDBObject(s)
    case s: Event => toMongoDBObject(s)
    case _ => throw new UnsupportedOperationException("Not supported")
  }

  implicit def toMongoDBObject(session: Session): DBObject = {
    MongoDBObject(
      "_id" -> session.id.map(i => new ObjectId(i)).getOrElse(new ObjectId()),
      "eventId" -> new ObjectId(session.eventId),
      "abstract" -> toMongoDBObject(session.abs),
      "published" -> session.published,
      "tags" -> session.tags.map(_.name),
      "keywords" -> session.keywords.map(_.name),
      "duration" -> session.duration.map(_.toString).orNull,
      "state" -> session.state.name,
      "last-modified" -> session.lastModified
    )
  }

  implicit def toMongoDBObject(contact: Contact): DBObject = {
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

  implicit def toMongoDBObject(abs: Abstract): DBObject = {
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

  def toMongoDBObject(speaker: Speaker): DBObject = {
    val obj = MongoDBObject("_id" -> new ObjectId(speaker.contactId), "name" -> speaker.name)
    speaker.image.map(a => "photo" -> new ObjectId(a.id.get))
    obj.putAll(speaker.bio.map(b => "bio" -> b).toMap)
    obj
  }
}
