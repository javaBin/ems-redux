package no.java.ems.storage

import com.mongodb.casbah.gridfs.{GridFSDBFile, GridFS}
import com.mongodb.casbah.Imports._
import java.net.URI
import java.util.Locale
import no.java.ems._
import java.util
import _root_.no.java.ems.model._
import java.util.{Date => JDate}
import java.io.{File, InputStream}
import model.Abstract
import model.Event
import model.Keyword
import model.Room
import model.Slot
import model.Speaker
import model.Tag
import no.java.ems.URIAttachment
import org.joda.time.DateTime
import _root_.no.java.ems.security.User
import com.mongodb.casbah.MongoCollection

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
    db("session").find(query.result()).sort(MongoDBObject("title" -> 1)).map(toSession(_, this)).toList
  }

  def getSessionsBySlug(eventId: String, slug: String) = db("session").find(
    MongoDBObject("eventId" -> eventId, "slug" -> slug)
  ).sort(MongoDBObject("abstract" -> MongoDBObject("title" -> 1))).map(toSession(_, this)).toList

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> id, "eventId" -> eventId)
  ).map(toSession(_, this))

  def saveSession(session: Session) = saveToMongo(session, db("session"))

  def saveSpeaker(eventId: String, sessionId: String, speaker: Speaker) = {
    val update = db("session").findOne(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speaker.id), MongoDBObject()
    ).isDefined

    val result = if (update) {
      val dbObject: MongoDBObject = toMongoDBObject(speaker)
      val toSave = dbObject.foldLeft(MongoDBObject.newBuilder){case (mongo, (key, value)) => mongo += ("speakers.$." + key -> value) }.result()
      db("session").update(
        MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speaker.id),
        MongoDBObject("$set" -> toSave)
      )
    }
    else {
      db("session").update(
        MongoDBObject("_id" -> sessionId, "eventId" -> eventId),
        MongoDBObject("$push" -> MongoDBObject("speakers" -> toMongoDBObject(speaker)))
      )
    }

    val error = result.getLastError
    if (error.ok()) {
      Right(speaker)
    }
    else {
      Left(error.getException)
    }
  }

  def removeSpeaker(eventId: String, sessionId: String, speakerId: String) = {
    val res = db("session").update(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId),
      MongoDBObject("$pull" -> MongoDBObject("sessions" -> MongoDBObject("_id" -> speakerId)))
    )
    val error = res.getLastError
    if (error.ok()) {
      Right("OK")
    }
    else {
      Left(error.getException)
    }
  }

  def getAttachment(id: String): Option[Attachment with Entity] = {
    val fs = GridFS(db)
    fs.findOne(MongoDBObject("_id" -> id)).map(GridFileAttachment)
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
      case e@FileAttachment(Some(id), _) => fs.findOne(MongoDBObject("_id" -> id)) match {
        case Some(f) => f
        case None => {
          val f = fs.createFile(getStream(e.underlying), e.name)
          f.put("_id", id)
          e.mediaType.foreach(mt => f.contentType = mt.toString)
          f
        }
      }
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

    val update = if (fromImport) coll.findOne(MongoDBObject("_id" -> entity.id.get), MongoDBObject()).isDefined else entity.id.isDefined
    val toSave = toMongoDBObject(stored, update)
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
    val abs = toAbstract(dbo, storage)
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

  def toSpeaker(dbo: DBObject, storage: MongoDBStorage) = {
    val m = wrapDBObj(dbo)
    Speaker(
      m.get("_id").map(_.toString).get,
      m.as[String]("name"),
      m.as[String]("email"),
      m.getAs[String]("zip-code"),
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

  def toMongoDBObject[A <: Entity#T](entity: A, update: Boolean = false): DBObject = entity match {
    case s: Session => toMongoDBObjectSession(s, update)
    case s: Event => toMongoDBObjectEvent(s, update)
    case _ => throw new UnsupportedOperationException("Not supported")
  }

  private def toMongoDBObjectEvent(event: Event, update: Boolean = false): DBObject = {
    val base = MongoDBObject(
      "name" -> event.name,
      "slug" -> event.slug,
      "start" -> event.start.toDate,
      "end" -> event.end.toDate,
      "venue" -> event.venue,
      "last-modified" -> event.lastModified.toDate
    )
    if (update) {
      MongoDBObject(
        "$set" -> base
      )
    } else {
      val obj = MongoDBObject(
        "_id" -> event.id.getOrElse(util.UUID.randomUUID().toString),
        "rooms" -> event.rooms.map(toRoom),
        "slots" -> event.slots.map(toSlot)
      ) ++ base
      obj
    }
  }

  private def toMongoDBObjectSession(session: Session, update: Boolean): DBObject = {
    val base = MongoDBObject(
      "slug" -> session.slug,
      "published" -> session.published,
      "tags" -> session.tags.map(_.name),
      "keywords" -> session.keywords.map(_.name),
      "state" -> session.state.name,
      "attachments" -> session.attachments.map(toMongoDBObject),
      "last-modified" -> session.lastModified.toDate
    ) ++ toMongoDBObject(session.abs, session.id.isDefined)

    if (update) {
      MongoDBObject(
        "$set" -> base
      )
    }
    else {
      val obj = MongoDBObject(
        "_id" -> session.id.getOrElse(util.UUID.randomUUID().toString),
        "eventId" -> session.eventId,
        "roomId" -> session.room.flatMap(_.id),
        "slotId" -> session.slot.flatMap(_.id),
        "speakers" -> session.speakers.map(toMongoDBObject(_))
      ) ++ base
      obj
    }
  }

  private def toMongoDBObject(att: URIAttachment): DBObject = {
    MongoDBObject(
      "href" -> att.href.toString,
      "name" -> att.name,
      "mime-type" -> att.mediaType.map(_.toString),
      "size" -> att.size
    )
  }

  private def toMongoDBObject(abs: Abstract, update: Boolean): DBObject = {
    val obj = MongoDBObject(
      "title" -> abs.title.trim,
      "body" -> abs.body,
      "summary" -> abs.summary,
      "equipment" -> abs.equipment,
      "outline" -> abs.outline,
      "audience" -> abs.audience,
      "format" -> abs.format.name,
      "level" -> abs.level.name,
      "language" -> abs.language.getLanguage
    )
    if (!update) {
      obj.put("speakers", abs.speakers.map(toMongoDBObject(_)))
    }
    obj
  }

  def toMongoDBObject(speaker: Speaker): DBObject = {
    MongoDBObject(
      "_id" -> speaker.id,
      "name" -> speaker.name,
      "email" -> speaker.email,
      "zip-code" -> speaker.zipCode,
      "bio" -> speaker.bio,
      "tags" -> speaker.tags.map(_.name),
      "photo" -> speaker.photo.map(_.id)
    )
  }

  private val toRoom: (Room) => DBObject = {
    r => MongoDBObject(
      "_id" -> r.id.getOrElse(util.UUID.randomUUID().toString),
      "name" -> r.name
    )
  }

  private val toSlot: (Slot) => DBObject = {
    ts => MongoDBObject(
      "_id" -> ts.id.getOrElse(util.UUID.randomUUID().toString),
      "start" -> ts.start.toDate,
      "end" -> ts.end.toDate
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

case class FileAttachment(id: Option[String], file: File) extends Entity with Attachment {
  lazy val underlying = StreamingAttachment(file)

  type T = FileAttachment

  def lastModified = underlying.lastModified

  def withId(id: String) = copy(id = Some(id))

  def name = underlying.name

  def size = Some(file.length())

  def mediaType = underlying.mediaType
}
