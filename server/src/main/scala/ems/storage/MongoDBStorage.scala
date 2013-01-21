package no.java.ems.storage

import com.mongodb.casbah.gridfs.{GridFSDBFile, GridFS}
import com.mongodb.casbah.Imports._
import java.net.URI
import java.util.Locale
import no.java.ems._
import java.util
import model._
import java.util.{Date => JDate}
import java.io.InputStream
import org.joda.time.DateTime
import security.User

trait MongoDBStorage  {

  import MongoMapper._
  import com.mongodb.casbah.commons.conversions.scala._

  DeregisterJodaTimeConversionHelpers()

  def db: MongoDB

  def getEvents() = db("event").find().sort(MongoDBObject("name" -> 1)).map(toEvent).toList

  def getEvent(id: String) = db("event").findOneByID(id).map(toEvent)

  def getEventsBySlug(name: String) = db("event").find(MongoDBObject("slug" -> name)).sort(MongoDBObject("name" -> 1)).map(toEvent).toList

  def saveEvent(event: Event) = saveToMongo(event, db("event"))

  def getSessions(eventId: String)(user: User) = {
    val query = MongoDBObject.newBuilder
    query += "eventId" -> eventId
    if (!user.authenticated) {
      query += "published" -> true
    }
    db("session").find(query.result()).sort(MongoDBObject("abstract" -> MongoDBObject("title" -> 1))).map(toSession(_, this)).toList
  }

  def getSessionsBySlug(eventId: String, slug: String) = db("session").find(
    MongoDBObject("eventId" -> eventId, "slug" -> slug)
  ).sort(MongoDBObject("abstract" -> MongoDBObject("title" -> 1))).map(toSession(_, this)).toList

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> id, "eventId" -> eventId)
  ).map(toSession(_, this))

  def saveSession(session: Session) = saveToMongo(session, db("session"))

  def getAttachment(id: String): Option[Attachment with Entity] = {
    val fs = GridFS(db)
    fs.findOne(new ObjectId(id)).map(GridFileAttachment)
  }

  def importEntity[A <: Entity](entity: A): Either[MongoException, A#T] = {
    entity match {
      case e: Event => saveToMongo[A](entity, db("event"), true)
      case e: Session => saveToMongo[A](entity, db("session"), true)
      case _ => throw new IllegalArgumentException("Unknown entity")
    }
  }

  def saveAttachment(att: Attachment) = {
    val fs = GridFS(db)
    val file = att match {
      case GridFileAttachment(f) => f
      case a => {
        fs.findOne(att.name) match {
          case Some(f) => f
          case None => {
            val f = fs.createFile(getStream(a), a.name)
            a.mediaType.foreach(mt => f.contentType = mt.toString)
            f
          }
        }
      }
    }
    file.metaData = MongoDBObject("last-modified" -> new JDate())
    file.save()
    getAttachment(file.id.toString).getOrElse(throw new IllegalArgumentException("Failed to save"))
  }

  def getChangedEvents(from: DateTime): Seq[Event] = {
    val q = "last-modified" $gte from.toDate
    db("event").find(q).map(toEvent).toSeq
  }

  def getChangedSessions(from: DateTime): Seq[Session] = {
    val q = "last-modified" $gte from.toDate
    db("session").find(q).map(toSession(_, this)).toSeq
  }

  //TODO: Make sure that we remove where its used as well.
  def removeAttachment(id: String) {
    val fs = GridFS(db)
    fs.remove(id)
  }

  def shutdown() {
    db.underlying.getMongo.close()
  }

  def getStream(att: Attachment): InputStream = att match {
    case u: URIAttachment => u.data
    case u: GridFileAttachment => u.data
    case u: StreamingAttachment => u.data
    case _ => throw new IllegalArgumentException("No stream available for %s".format(att.getClass.getName))
  }

  def saveEntity[T <: Entity](entity: T) = entity match {
    case e: Event => saveEvent(e)
    case s: Session => saveSession(s)
    case _ => throw new IllegalArgumentException("Usupported entity: " + entity)
  }

  private def saveToMongo[A <: Entity](entity: A, coll: MongoCollection, fromImport: Boolean = false): Either[MongoException, A#T] = {
    val stored = withId(entity)
    val toSave = toMongoDBObject(stored)

    val update = if (fromImport) coll.findOneByID(entity.id.get, MongoDBObject()).isDefined else entity.id.isDefined
    if (update) {
      coll.update(MongoDBObject("_id" -> entity.id.get), toSave)
    }
    else {
      coll.insert(toSave, WriteConcern.Safe)
    }
    val lastError = coll.lastError()
    if (lastError.ok()) {
      Right(stored)
    }
    else {
      Left(lastError.getException)
    }
  }

  private def withId[Y <: Entity](entity: Y): Y#T = {
    val id = entity.id.getOrElse(util.UUID.randomUUID().toString)
    entity.withId(id)
  }

}

private[storage] object MongoMapper {

  import com.mongodb.casbah.Imports._
  import com.mongodb.casbah.commons.conversions.scala._

  DeregisterJodaTimeConversionHelpers()

  val toEvent: (DBObject) => Event = (dbo) => {
    val m = wrapDBObj(dbo)
    Event(
      m.get("_id").map(_.toString),
      m.getAsOrElse("name", "No Name"),
      m.as[String]("slug"),
      m.getAsOrElse[JDate]("start", new JDate()),
      m.getAsOrElse[JDate]("end", new JDate()),
      m.getAsOrElse("venue", "Unknown"),
      m.getAsOrElse[Seq[_]]("rooms", Seq()).map(o => {
        val w = wrapDBObj(o.asInstanceOf[DBObject])
        Room(w.getAs[String]("_id"), w.as[String]("name"))
      }),
      m.getAsOrElse[Seq[_]]("slots", Seq()).map(o => {
        val w = wrapDBObj(o.asInstanceOf[DBObject])
        Slot(w.getAs[String]("_id"), w.getAsOrElse[JDate]("start", new JDate(0L)), w.getAsOrElse[JDate]("end", new JDate(0L)))
      }),
      m.getAsOrElse[JDate]("last-modified", new JDate())
    )
  }

  def toSession(dbo: DBObject, storage: MongoDBStorage) = {
    val m = wrapDBObj(dbo)
    val abs = m.getAs[DBObject]("abstract").map(toAbstract(_, storage)).getOrElse(new Abstract("No Title"))
    val eventId = m.get("eventId").map(_.toString).getOrElse(throw new IllegalArgumentException("No eventId"))
    val event = storage.getEvent(eventId).getOrElse(throw new IllegalArgumentException("No Event"))
    val slot = event.slots.find(_.id == m.get("slotId").map(_.toString))
    val room = event.rooms.find(_.id == m.get("roomId").map(_.toString))

    Session(
      m.get("_id").map(_.toString),
      m.get("eventId").map(_.toString).getOrElse(throw new IllegalArgumentException("No eventId")),
      m.get("slug").map(_.toString).get,
      room,
      slot,
      abs,
      m.getAs[String]("state").map(State(_)).getOrElse(State.Pending),
      m.getAs[Boolean]("published").getOrElse(false),
      m.getAsOrElse[Seq[_]]("tags", Seq.empty).map(t => Tag(t.toString)).toSet[Tag],
      m.getAsOrElse[Seq[_]]("keywords", Seq.empty).map(k => Keyword(k.toString)).toSet[Keyword],
      Nil,
      m.getAsOrElse[JDate]("last-modified", new JDate())
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
      m.getAs[String]("equipment"),
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
      m.as[String]("email"),
      m.getAs[String]("bio"),
      m.getAsOrElse[Seq[_]]("tags", Seq.empty).map(t => Tag(t.toString)).toSet[Tag],
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

  def toMongoDBObject[A <: Entity#T](entity: A): DBObject = entity match {
    case s: Session => toMongoDBObject(s)
    case s: Event => toMongoDBObject(s)
    case _ => throw new UnsupportedOperationException("Not supported")
  }

  private def toMongoDBObject(event: Event): DBObject = {
    MongoDBObject(
      "_id" -> event.id.getOrElse(util.UUID.randomUUID().toString),
      "name" -> event.name,
      "slug" -> event.slug,
      "start" -> event.start.toDate,
      "end" -> event.end.toDate,
      "venue" -> event.venue,
      "rooms" -> event.rooms.map(r => MongoDBObject(
        "_id" -> r.id.getOrElse(util.UUID.randomUUID().toString),
        "name" -> r.name
      )),
      "slots" -> event.slots.map(ts => MongoDBObject(
        "_id" -> ts.id.getOrElse(util.UUID.randomUUID().toString),
        "start" -> ts.start.toDate,
        "end" -> ts.end.toDate
      )),
      "last-modified" -> event.lastModified.toDate
    )
  }

  private def toMongoDBObject(session: Session): DBObject = {
    MongoDBObject(
      "_id" -> session.id.getOrElse(util.UUID.randomUUID().toString),
      "eventId" -> session.eventId,
      "slug" -> session.slug,
      "abstract" -> toMongoDBObject(session.abs),
      "published" -> session.published,
      "tags" -> session.tags.map(_.name),
      "keywords" -> session.keywords.map(_.name),
      "state" -> session.state.name,
      "attachments" -> session.attachments.map(toMongoDBObject),
      "roomId" -> session.room.flatMap(_.id),
      "slotId" -> session.slot.flatMap(_.id),
      "last-modified" -> session.lastModified.toDate
    )
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
      "title" -> abs.title.trim,
      "body" -> abs.body,
      "summary" -> abs.summary,
      "equipment" -> abs.equipment,
      "outline" -> abs.outline,
      "audience" -> abs.audience,
      "format" -> abs.format.name,
      "level" -> abs.level.name,
      "language" -> abs.language.getLanguage,
      "speakers" -> abs.speakers.map(toMongoDBObject)
    )
  }

  private def toMongoDBObject(speaker: Speaker): DBObject = {
    MongoDBObject(
      "_id" -> speaker.id,
      "name" -> speaker.name,
      "email" -> speaker.email,
      "bio" -> speaker.bio,
      "tags" -> speaker.tags.map(_.name),
      "photo" -> speaker.photo.map(_.id)
    )
  }
}

case class GridFileAttachment(file: GridFSDBFile) extends Attachment with Entity {
  type T = GridFileAttachment

  def data = file.inputStream

  def name = file.filename.get

  def size = Some(file.length)

  def mediaType = file.contentType.flatMap(MIMEType(_))

  def lastModified = file.metaData.getAsOrElse[JDate]("last-modified", new JDate())

  def id = file.id match {
    case null => None
    case i => Some(i.toString)
  }

  def withId(id: String) = {
    file.put("_id", id)
    this
  }
}
