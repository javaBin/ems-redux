package no.java.ems.storage

import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.Imports._
import no.java.ems._
import security.User
import java.util
import java.util.{Date => JDate}
import java.io.InputStream
import model._
import no.java.ems.URIAttachment
import org.joda.time.DateTime

trait MongoDBStorage  {

  import com.mongodb.casbah.commons.conversions.scala._

  DeregisterJodaTimeConversionHelpers()

  def db: MongoDB

  def getEvents() = db("event").find().sort(MongoDBObject("name" -> 1)).map(Event.apply).toList

  def getEvent(id: String) = db("event").findOneByID(id).map(Event.apply)

  def getEventsBySlug(name: String) = db("event").find(MongoDBObject("slug" -> name)).sort(MongoDBObject("name" -> 1)).map(Event.apply).toList

  def saveEvent(event: Event): Either[MongoException, Event] = saveOrUpdate(event, event.toMongo, db("event"))

  def getSlots(eventId: String): Seq[Slot] = getEvent(eventId).map(_.slots).getOrElse(Nil)

  def getSlot(eventId: String, id: String): Option[Slot] = getEvent(eventId).flatMap(_.slots.find(_.id == id))

  def saveSlot(eventId: String, slot: Slot): Either[Exception, Slot] = {
    val slotId = slot.id.getOrElse(util.UUID.randomUUID().toString)

    val update = db("event").findOne(
      MongoDBObject("_id" -> eventId, "slots._id" -> slotId), MongoDBObject()
    ).isDefined

    val result = if (update) {
      val dbObject: MongoDBObject = slot.toMongo
      val toSave = dbObject.foldLeft(MongoDBObject.newBuilder){case (mongo, (key, value)) => mongo += ("slots.$." + key -> value) }.result()
      db("event").update(
        MongoDBObject("_id" -> eventId, "slots._id" -> slotId),
        MongoDBObject("$set" -> toSave)
      )
    }
    else {
      db("event").update(
        MongoDBObject("_id" -> eventId),
        MongoDBObject("$push" -> MongoDBObject("slots" -> slot.toMongo))
      )
    }

    val error = result.getLastError
    if (error.ok()) {
      Right(slot)
    }
    else {
      Left(error.getException)
    }
  }

  def removeSlot(eventId: String, id: String): Either[Exception, String] = Left(new UnsupportedOperationException())

  def getRooms(eventId: String): Seq[Room] = getEvent(eventId).map(_.rooms).getOrElse(Nil)

  def getRoom(eventId: String, id: String): Option[Room] = getEvent(eventId).flatMap(_.rooms.find(_.id == id))

  def saveRoom(eventId: String, room: Room): Either[Exception, Room] = Left(new UnsupportedOperationException())

  def removeRoom(eventId: String, id: String): Either[Exception, String] = Left(new UnsupportedOperationException())


  def getSessions(eventId: String)(user: User) = {
    val query = MongoDBObject.newBuilder
    query += "eventId" -> eventId
    if (!user.authenticated) {
      query += "published" -> true
    }
    db("session").find(query.result()).sort(MongoDBObject("title" -> 1)).map(Session(_, this)).toList
  }

  def getSessionsBySlug(eventId: String, slug: String) = db("session").find(
    MongoDBObject("eventId" -> eventId, "slug" -> slug)
  ).sort(MongoDBObject("abstract" -> MongoDBObject("title" -> 1))).map(Session(_, this)).toList

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> id, "eventId" -> eventId)
  ).map(Session(_, this))

  def saveSession(session: Session) = saveOrUpdate(session, session.toMongo, db("session"))

  def getSpeaker(eventId: String, sessionId: String, speakerId: String) = db("session").findOne(
    MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speakerId),
    MongoDBObject("speakers" -> 1)
  ).flatMap(_.getAs[MongoDBList]("speakers").headOption.map(_.asInstanceOf[DBObject])).map(Speaker(_, this))

  def saveSpeaker(eventId: String, sessionId: String, speaker: Speaker) = {
    val speakerId = speaker.id.getOrElse(util.UUID.randomUUID().toString)
    val update = db("session").findOne(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speakerId), MongoDBObject()
    ).isDefined

    val dbObject: MongoDBObject = speaker.toMongo
    val result = if (update) {
      val toSave = dbObject.foldLeft(MongoDBObject.newBuilder){case (mongo, (key, value)) => mongo += ("speakers.$." + key -> value) }.result()
      db("session").update(
        MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speakerId),
        MongoDBObject("$set" -> toSave)
      )
    }
    else {
      db("session").update(
        MongoDBObject("_id" -> sessionId, "eventId" -> eventId),
        MongoDBObject("$push" -> MongoDBObject("speakers" -> dbObject))
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

  def updateSpeakerWithPhoto(eventId: String, sessionId: String, speakerId: String, photo: Attachment with Entity[Attachment]) = {
    val toSave = MongoDBObject("speakers.$.photo" -> photo.id.get)
    val result = db("session").update(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speakerId),
      MongoDBObject("$set" -> toSave)
    )

    val error = result.getLastError
    if (error.ok()) {
      Right(photo)
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

  def getAttachment(id: String): Option[Attachment with Entity[Attachment]] = {
    val fs = GridFS(db)
    fs.findOne(MongoDBObject("_id" -> id)).map(GridFileAttachment)
  }

  def importEntity[A <: Entity[A]](entity: A): Either[MongoException, A] = {
    entity match {
      case e: Event => saveOrUpdate[A](entity, e.toMongo, db("event"), true)
      case e: Session => saveOrUpdate[A](entity, e.toMongo, db("session"), true)
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
    db("event").find(q).map(Event.apply).toSeq
  }

  def getChangedSessions(from: DateTime): Seq[Session] = {
    val q = "last-modified" $gte from.toDate
    db("session").find(q).map(Session(_, this)).toSeq
  }

  //TODO: Make sure that we remove where its used as well.
  def removeAttachment(id: String) {
    val fs = GridFS(db)
    fs.remove(id)
  }

  def getStream(att: Attachment): InputStream = att match {
    case u: URIAttachment => u.data
    case u: GridFileAttachment => u.data
    case u: StreamingAttachment => u.data
    case _ => throw new IllegalArgumentException("No stream available for %s".format(att.getClass.getName))
  }

  def shutdown() {
    db.underlying.getMongo.close()
  }

  private def saveOrUpdate[A <: Entity[A]](entity: A, toMongoDBObject: (Boolean) => DBObject, coll: MongoCollection, fromImport: Boolean = false): Either[MongoException, A] = {
    val update = if (fromImport) coll.findOne(MongoDBObject("_id" -> entity.id.get), MongoDBObject()).isDefined else entity.id.isDefined
    val toSave = toMongoDBObject(update)
    if (update) {
      coll.update(MongoDBObject("_id" -> entity.id.get), toSave)
    }
    else {
      coll.insert(toSave, WriteConcern.Safe)
    }
    val lastError = coll.lastError()
    if (lastError.ok()) {
      Right(entity)
    }
    else {
      Left(lastError.getException)
    }
  }
}
