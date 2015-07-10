package ems.storage

/*
import scala.util.control.Exception.nonFatalCatch
import com.mongodb.casbah.Imports._
import ems._
import security.User
import java.util
import util.UUID
import model._
import org.joda.time.DateTime
import com.mongodb.casbah.commons.MongoDBObject

trait MongoDBStorage extends DBStorage {

  import com.mongodb.casbah.commons.conversions.scala._

  DeregisterJodaTimeConversionHelpers()

  def db: MongoDB

  def binary: BinaryStorage

  def getEvents() = db("event").find().sort(MongoDBObject("name" -> 1)).map(Event.apply).toVector

  def getEventsWithSessionCount(user: User) = getEvents().map(e => EventWithSessionCount(e, e.id.map(id => getSessionCount(id)(user)).getOrElse(0)))

  def getEvent(id: String) = db("event").findOneByID(id).map(Event.apply)

  def getEventsBySlug(name: String) = db("event").find(MongoDBObject("slug" -> name)).sort(MongoDBObject("name" -> 1)).map(Event.apply).toVector

  def saveEvent(event: Event): Either[Throwable, Event] = saveOrUpdate(event, (e: Event, update) => e.toMongo(update), db("event"))

  def getSlots(eventId: String, parent: Option[String] = None): Vector[Slot] = {
    val obj = MongoDBObject("eventId" -> eventId, "parentId" -> parent.orNull)
    db("slot").find(obj).map(Slot.apply).toVector
  }

  def getAllSlots(eventId: String): Vector[SlotTree] = getSlots(eventId).map(s => SlotTree(s, getSlots(s.eventId, s.id)))

  def getSlot(id: String): Option[Slot] = {
    db("slot").findOne(MongoDBObject("_id" -> id)).map(Slot.apply)
  }

  def saveSlot(slot: Slot): Either[Throwable, Slot] = {
    saveOrUpdate(slot, (s: Slot, update: Boolean) => s.toMongo, db("slot"))
  }

  def removeSlot(eventId: String, id: String): Either[Throwable, String] = Left(new UnsupportedOperationException())

  def getRooms(eventId: String): Vector[Room] = getEvent(eventId).map(_.rooms).getOrElse(Nil).toVector

  def getRoom(eventId: String, id: String): Option[Room] = getRooms(eventId).find(r => r.id.contains(id))

  def saveRoom(eventId: String, room: Room): Either[Throwable, Room] = Left(new UnsupportedOperationException())

  def removeRoom(eventId: String, id: String): Either[Throwable, String] = Left(new UnsupportedOperationException())

  def getSessions(eventId: String)(user: User) = {
    val query = MongoDBObject.newBuilder
    query += "eventId" -> eventId
    if (!user.authenticated) {
      query += "published" -> true
    }
    db("session").find(query.result()).sort(MongoDBObject("title" -> 1)).map(Session(_, this)).toVector
  }

  def getSessionCount(eventId: String)(user: User): Int = {
    val query = MongoDBObject.newBuilder
    query += "eventId" -> eventId
    if (!user.authenticated) {
      query += "published" -> true
    }
    db("session").find(query.result(), MongoDBObject("_id" -> 1)).size
  }

  def getSessionsBySlug(eventId: String, slug: String) = db("session").find(
    MongoDBObject("eventId" -> eventId, "slug" -> slug)
  ).sort(MongoDBObject("abstract" -> MongoDBObject("title" -> 1))).map(Session(_, this)).toVector

  def getSession(eventId: String, id: String) = db("session").findOne(
    MongoDBObject("_id" -> id, "eventId" -> eventId)
  ).map(Session(_, this))

  def saveSession(session: Session) = saveOrUpdate(session, (s: Session, update) => s.toMongo(update), db("session"))

  def publishSessions(eventId: String, sessions: Seq[String]): Either[Throwable, Unit] = nonFatalCatch.either {
    val result = db("session").update(MongoDBObject("eventId" -> eventId, "_id" -> MongoDBObject("$in" -> sessions)), MongoDBObject("$set" -> MongoDBObject("published" -> true)), multi = true)

    ()
  }

  def saveSlotInSession(eventId: String, sessionId: String, slot: Slot) = saveOrUpdate(
    getSession(eventId, sessionId).get,
    (s: Session, update) => MongoDBObject("$set" -> MongoDBObject("slotId" -> slot.id.get, "last-modified" -> DateTime.now.toDate)),
    db("session")
  )

  def saveRoomInSession(eventId: String, sessionId: String, room: Room) = saveOrUpdate(
    getSession(eventId, sessionId).get,
    (s: Session, update) => MongoDBObject("$set" -> MongoDBObject("roomId" -> room.id.get, "last-modified" -> DateTime.now.toDate)),
    db("session")
  )

  def saveAttachment(eventId: String, sessionId: String, attachment: URIAttachment) = nonFatalCatch.either {
    val withId = if (attachment.id.isDefined) attachment else attachment.withId(util.UUID.randomUUID().toString)
    val speakerId = withId.id.get
    val update = db("session").findOne(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "attachments._id" -> speakerId), MongoDBObject()
    ).isDefined

    val dbObject: MongoDBObject = withId.toMongo
    if (update) {
      val toSave = dbObject.foldLeft(MongoDBObject.newBuilder){case (mongo, (key, value)) => mongo += ("attachments.$." + key -> value) }.result()
      db("session").update(
        MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "attachments._id" -> speakerId),
        MongoDBObject("$set" -> toSave)
      )
    }
    else {
      db("session").update(
        MongoDBObject("_id" -> sessionId, "eventId" -> eventId),
        MongoDBObject("$push" -> MongoDBObject("attachments" -> dbObject))
      )
    }
    withId
  }

  def removeAttachment(eventId: String, sessionId: String, id: String) = nonFatalCatch.either {
    db("session").update(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "attachments._id" -> id),
      MongoDBObject("$pull" -> MongoDBObject("attachments.$._id" -> id))
    )
    ()
  }

  def getSpeaker(eventId: String, sessionId: String, speakerId: String) = {
    getSession(eventId, sessionId).flatMap(_.speakers.find(_.id.contains(speakerId)))
  }

  def saveSpeaker(eventId: String, sessionId: String, speaker: Speaker) = nonFatalCatch.either {
    val withId = if (speaker.id.isDefined) speaker else speaker.withId(util.UUID.randomUUID().toString)
    val speakerId = withId.id.get
    val update = db("session").findOne(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speakerId), MongoDBObject()
    ).isDefined

    val dbObject: MongoDBObject = withId.toMongo
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

    withId
  }

  def removeSession(sessionId: String): Either[Throwable, Unit] = nonFatalCatch.either(db("session").remove(MongoDBObject("_id" -> sessionId)))

  def updateSpeakerWithPhoto(eventId: String, sessionId: String, speakerId: String, photo: Attachment with Entity[Attachment]) = nonFatalCatch.either {
    val toSave = MongoDBObject("speakers.$.photo" -> photo.id.get)
    val result = db("session").update(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId, "speakers._id" -> speakerId),
      MongoDBObject("$set" -> toSave)
    )
    photo
  }

  def removeSpeaker(eventId: String, sessionId: String, speakerId: String): Either[Throwable, Unit] = nonFatalCatch.either {
    db("session").update(
      MongoDBObject("_id" -> sessionId, "eventId" -> eventId),
      MongoDBObject("$pull" -> MongoDBObject("sessions" -> MongoDBObject("_id" -> speakerId)))
    )
  }

  def importSession(session: Session): Either[Throwable, Session] = {
    val either = saveOrUpdate(session, (o: Session, update) => o.toMongo(update), db("session"), fromImport = true)
    session.speakers.foreach(sp => sp.photo.foreach(ph => updateSpeakerWithPhoto(session.eventId, session.id.get, sp.id.get, ph).fold(
      ex => throw ex,
      _ => ()
    )))
    either
  }

  def importEvent(event: Event): Either[Throwable, Event] = {
    saveOrUpdate(event, (o: Event, update) => o.toMongo(update), db("event"), fromImport = true)
  }

  def getChangedSessions(from: DateTime)(implicit u: User): Vector[Session] = {
    val builder = MongoDBObject.newBuilder
    builder ++= ("last-modified" $gte from.toDate)
    if (!u.authenticated) {
      builder += "published" -> true
    }
    db("session").find(builder.result()).map(Session(_, this)).toVector
  }

  def status(): String = {
    try {
      db.command(MongoDBObject("ping" -> 1)).get("ok").toString match {
        case "1.0" => "ok"
        case _ => "down"
      }
    } catch {
      case e: Exception => "down"
    }
  }

  def shutdown() {
    db.underlying.getMongo.close()
  }

  private def saveOrUpdate[A <: Entity[A]](entity: A, toMongoDBObject: (A, Boolean) => DBObject, coll: MongoCollection, fromImport: Boolean = false): Either[Throwable, A] = nonFatalCatch.either {
    val objectWithId = withId(entity)
    val update = if (fromImport) coll.findOne(MongoDBObject("_id" -> entity.id.get), MongoDBObject()).isDefined else entity.id.isDefined
    val toSave = toMongoDBObject(objectWithId, update)
    if (update) {
      coll.update(MongoDBObject("_id" -> entity.id.get), toSave)
    }
    else {
      coll.insert(toSave, WriteConcern.Safe)
    }
    objectWithId
  }

  private def delete[A <: Entity[A]](entity: A, coll: MongoCollection): Either[Throwable, Unit] = nonFatalCatch.either {
    entity.id.foreach{ id =>
      coll.remove(MongoDBObject("_id" -> id))
    }
  }

  private def withId[A <: Entity[A]](entity: A): A = {
    if (entity.id.isDefined) entity else entity.withId(UUID.randomUUID().toString)
  }

}
*/
