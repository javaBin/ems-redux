package ems.storage

import java.sql.{PreparedStatement, ResultSet}


// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.driver.PostgresDriver
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.driver.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}
  import argonaut._, Argonaut._
  import org.postgresql.util.PGobject
  import com.github.tototoshi.slick.PostgresJodaSupport._
  import org.joda.time.DateTime

  implicit object JsonJdbcType extends profile.DriverJdbcType[Json]{
    override def sqlType = java.sql.Types.OTHER

    override def getValue(r: ResultSet, idx: Int) =
      Option(r.getObject(idx)).map(_.asInstanceOf[PGobject].getValue.parse.fold(s ⇒ throw new IllegalStateException(s), identity)).getOrElse(Json.jEmptyObject)

    override def setValue(v: Json, p: PreparedStatement, idx: Int) =
      p.setObject(idx, pgObject(v))

    def pgObject(v: Json): PGobject = {
      val pgObject = new PGobject()
      pgObject.setType("json")
      pgObject.setValue(v.nospaces)
      pgObject
    }

    override def updateValue(v: Json, r: ResultSet, idx: Int) = r.updateObject(idx, pgObject(v))
  }

  /** Entity class storing rows of table Event
   *  @param id Database column id SqlType(uuid), PrimaryKey
   *  @param name Database column name SqlType(varchar), Length(256,true)
   *  @param venue Database column venue SqlType(varchar), Length(512,true)
   *  @param slug Database column slug SqlType(varchar), Length(256,true)
   *  @param lastmodified Database column lastmodified SqlType(timestamptz) */
  case class EventRow(id: java.util.UUID, name: String, venue: String, slug: String, lastmodified: DateTime)
  /** GetResult implicit for fetching EventRow objects using plain SQL queries */
  implicit def GetResultEventRow(implicit e0: GR[java.util.UUID], e1: GR[String], e2: GR[DateTime]): GR[EventRow] = GR{
    prs => import prs._
    EventRow.tupled((<<[java.util.UUID], <<[String], <<[String], <<[String], <<[DateTime]))
  }
  /** Table description of table event. Objects of this class serve as prototypes for rows in queries. */
  class Events(_tableTag: Tag) extends Table[EventRow](_tableTag, "event") {
    def * = (id, name, venue, slug, lastmodified) <> (EventRow.tupled, EventRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(name), Rep.Some(venue), Rep.Some(slug), Rep.Some(lastmodified)).shaped.<>({r=>import r._; _1.map(_=> EventRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid), PrimaryKey */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
    /** Database column name SqlType(varchar), Length(256,true) */
    val name: Rep[String] = column[String]("name", O.Length(256,varying=true))
    /** Database column venue SqlType(varchar), Length(512,true) */
    val venue: Rep[String] = column[String]("venue", O.Length(512,varying=true))
    /** Database column slug SqlType(varchar), Length(256,true) */
    val slug: Rep[String] = column[String]("slug", O.Length(256,varying=true))
    /** Database column lastmodified SqlType(timestamptz) */
    val lastmodified: Rep[DateTime] = column[DateTime]("lastmodified")

    /** Uniqueness Index over (slug) (database name event_slug_key) */
    val index1 = index("event_slug_key", slug, unique=true)
  }
  /** Collection-like TableQuery object for table Event */
  lazy val Events = new TableQuery(tag => new Events(tag))

  /** Entity class storing rows of table Room
   *  @param id Database column id SqlType(uuid)
   *  @param eventid Database column eventid SqlType(uuid)
   *  @param name Database column name SqlType(varchar), Length(256,true)
   *  @param lastmodified Database column lastmodified SqlType(timestamptz) */
  case class RoomRow(id: java.util.UUID, eventid: java.util.UUID, name: String, lastmodified: DateTime)
  /** GetResult implicit for fetching RoomRow objects using plain SQL queries */
  implicit def GetResultRoomRow(implicit e0: GR[java.util.UUID], e1: GR[String], e2: GR[DateTime]): GR[RoomRow] = GR{
    prs => import prs._
    RoomRow.tupled((<<[java.util.UUID], <<[java.util.UUID], <<[String], <<[DateTime]))
  }
  /** Table description of table room. Objects of this class serve as prototypes for rows in queries. */
  class Rooms(_tableTag: Tag) extends Table[RoomRow](_tableTag, "room") {
    def * = (id, eventid, name, lastmodified) <> (RoomRow.tupled, RoomRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(eventid), Rep.Some(name), Rep.Some(lastmodified)).shaped.<>({r=>import r._; _1.map(_=> RoomRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid) */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id")
    /** Database column eventid SqlType(uuid) */
    val eventid: Rep[java.util.UUID] = column[java.util.UUID]("eventid")
    /** Database column name SqlType(varchar), Length(256,true) */
    val name: Rep[String] = column[String]("name", O.Length(256,varying=true))
    /** Database column lastmodified SqlType(timestamptz) */
    val lastmodified: Rep[DateTime] = column[DateTime]("lastmodified")

    /** Primary key of Room (database name room_pk) */
    val pk = primaryKey("room_pk", (id, eventid))

    /** Foreign key referencing Event (database name room_eid) */
    lazy val eventFk = foreignKey("room_eid", eventid, Events)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Uniqueness Index over (id) (database name room_id_key) */
    val index1 = index("room_id_key", id, unique=true)
  }
  /** Collection-like TableQuery object for table Room */
  lazy val Rooms = new TableQuery(tag => new Rooms(tag))

  /** Entity class storing rows of table Session
   *  @param id Database column id SqlType(uuid)
   *  @param eventid Database column eventid SqlType(uuid)
   *  @param slug Database column slug SqlType(varchar), Length(256,true)
   *  @param abs Database column abstract SqlType(jsonb)
   *  @param state Database column state SqlType(varchar), Length(20,true)
   *  @param published Database column published SqlType(bool), Default(Some(false))
   *  @param roomid Database column roomid SqlType(uuid), Default(None)
   *  @param slotid Database column slotid SqlType(uuid), Default(None)
   *  @param lastmodified Database column lastmodified SqlType(timestamptz) */
  case class SessionRow(id: java.util.UUID, eventid: java.util.UUID, slug: String, abs: Json, state: String, published: Boolean = false, roomid: Option[java.util.UUID] = None, slotid: Option[java.util.UUID] = None, lastmodified: DateTime)
  /** GetResult implicit for fetching SessionRow objects using plain SQL queries */
  implicit def GetResultSessionRow(implicit e0: GR[java.util.UUID], e1: GR[String], e2: GR[Json], e3: GR[Boolean], e4: GR[Option[java.util.UUID]], e5: GR[DateTime]): GR[SessionRow] = GR{
    prs => import prs._
    SessionRow.tupled((<<[java.util.UUID], <<[java.util.UUID], <<[String], <<[Json], <<[String], <<[Boolean], <<?[java.util.UUID], <<?[java.util.UUID], <<[DateTime]))
  }
  /** Table description of table session. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: abstract */
  class Sessions(_tableTag: Tag) extends Table[SessionRow](_tableTag, "session") {
    def * = (id, eventid, slug, abs, state, published, roomid, slotid, lastmodified) <> (SessionRow.tupled, SessionRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(eventid), Rep.Some(slug), Rep.Some(abs), Rep.Some(state), Rep.Some(published), roomid, slotid, Rep.Some(lastmodified)).shaped.<>({r=>import r._; _1.map(_=> SessionRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid) */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id")
    /** Database column eventid SqlType(uuid) */
    val eventid: Rep[java.util.UUID] = column[java.util.UUID]("eventid")
    /** Database column slug SqlType(varchar), Length(256,true) */
    val slug: Rep[String] = column[String]("slug", O.Length(256,varying=true))
    /** Database column abstract SqlType(jsonb), Length(2147483647,false)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val abs: Rep[Json] = column[Json]("abstract")
    /** Database column state SqlType(varchar), Length(20,true) */
    val state: Rep[String] = column[String]("state", O.Length(20,varying=true))
    /** Database column published SqlType(bool), Default(Some(false)) */
    val published: Rep[Boolean] = column[Boolean]("published", O.Default(false))
    /** Database column roomid SqlType(uuid), Default(None) */
    val roomid: Rep[Option[java.util.UUID]] = column[Option[java.util.UUID]]("roomid", O.Default(None))
    /** Database column slotid SqlType(uuid), Default(None) */
    val slotid: Rep[Option[java.util.UUID]] = column[Option[java.util.UUID]]("slotid", O.Default(None))
    /** Database column lastmodified SqlType(timestamptz) */
    val lastmodified: Rep[DateTime] = column[DateTime]("lastmodified")

    /** Primary key of Session (database name session_pk) */
    val pk = primaryKey("session_pk", (id, eventid))

    /** Foreign key referencing Event (database name session_eid) */
    lazy val eventFk = foreignKey("session_eid", eventid, Events)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Room (database name session_rid) */
    lazy val roomFk = foreignKey("session_rid", roomid, Rooms)(r => Rep.Some(r.id), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Slot (database name session_sid) */
    lazy val slotFk = foreignKey("session_sid", slotid, Slots)(r => Rep.Some(r.id), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Uniqueness Index over (id) (database name session_id_key) */
    val index1 = index("session_id_key", id, unique=true)
    /** Uniqueness Index over (eventid,slug) (database name session_slug_unique) */
    val index2 = index("session_slug_unique", (eventid, slug), unique=true)
  }
  /** Collection-like TableQuery object for table Session */
  lazy val Sessions = new TableQuery(tag => new Sessions(tag))

  /** Entity class storing rows of table Slot
   *  @param id Database column id SqlType(uuid)
   *  @param eventid Database column eventid SqlType(uuid)
   *  @param parentid Database column parentid SqlType(uuid), Default(None)
   *  @param start Database column start SqlType(timestamptz)
   *  @param duration Database column duration SqlType(int4)
   *  @param lastmodified Database column lastmodified SqlType(timestamptz) */
  case class SlotRow(id: java.util.UUID, eventid: java.util.UUID, parentid: Option[java.util.UUID] = None, start: DateTime, duration: Int, lastmodified: DateTime)
  /** GetResult implicit for fetching SlotRow objects using plain SQL queries */
  implicit def GetResultSlotRow(implicit e0: GR[java.util.UUID], e1: GR[Option[java.util.UUID]], e2: GR[java.sql.Timestamp], e3: GR[Int], e4: GR[DateTime]): GR[SlotRow] = GR{
    prs => import prs._
    SlotRow.tupled((<<[java.util.UUID], <<[java.util.UUID], <<?[java.util.UUID], <<[DateTime], <<[Int], <<[DateTime]))
  }
  /** Table description of table slot. Objects of this class serve as prototypes for rows in queries. */
  class Slots(_tableTag: Tag) extends Table[SlotRow](_tableTag, "slot") {
    def * = (id, eventid, parentid, start, duration, lastmodified) <> (SlotRow.tupled, SlotRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(eventid), parentid, Rep.Some(start), Rep.Some(duration), Rep.Some(lastmodified)).shaped.<>({r=>import r._; _1.map(_=> SlotRow.tupled((_1.get, _2.get, _3, _4.get, _5.get, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid) */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id")
    /** Database column eventid SqlType(uuid) */
    val eventid: Rep[java.util.UUID] = column[java.util.UUID]("eventid")
    /** Database column parentid SqlType(uuid), Default(None) */
    val parentid: Rep[Option[java.util.UUID]] = column[Option[java.util.UUID]]("parentid", O.Default(None))
    /** Database column start SqlType(timestamptz) */
    val start: Rep[DateTime] = column[DateTime]("start")
    /** Database column duration SqlType(int4) */
    val duration: Rep[Int] = column[Int]("duration")
    /** Database column lastmodified SqlType(timestamptz) */
    val lastmodified: Rep[DateTime] = column[DateTime]("lastmodified")

    /** Primary key of Slot (database name slot_pk) */
    val pk = primaryKey("slot_pk", (id, eventid))

    /** Foreign key referencing Event (database name slot_eid) */
    lazy val eventFk = foreignKey("slot_eid", eventid, Events)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Slot (database name slot_pid) */
    lazy val slotFk = foreignKey("slot_pid", parentid, Slots)(r => Rep.Some(r.id), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Uniqueness Index over (id) (database name slot_id_key) */
    val index1 = index("slot_id_key", id, unique=true)
  }
  /** Collection-like TableQuery object for table Slot */
  lazy val Slots = new TableQuery(tag => new Slots(tag))

  /** Entity class storing rows of table Speaker
   *  @param id Database column id SqlType(uuid)
   *  @param sessionid Database column sessionid SqlType(uuid)
   *  @param email Database column email SqlType(varchar), Length(512,true)
   *  @param attributes Database column attributes SqlType(jsonb), Length(2147483647,false)
   *  @param photo Database column photo SqlType(varchar), Length(1024,true), Default(None)
   *  @param lastmodified Database column lastmodified SqlType(timestamptz) */
  case class SpeakerRow(id: java.util.UUID, sessionid: java.util.UUID, email: String, attributes: Json, photo: Option[String] = None, lastmodified: DateTime = DateTime.now())
  /** GetResult implicit for fetching SpeakerRow objects using plain SQL queries */
  implicit def GetResultSpeakerRow(implicit e0: GR[java.util.UUID], e1: GR[Json], e2: GR[Option[String]], e3: GR[DateTime]): GR[SpeakerRow] = GR{
    prs => import prs._
    SpeakerRow.tupled((<<[java.util.UUID], <<[java.util.UUID], <<[String], <<[Json], <<?[String], <<[DateTime]))
  }
  /** Table description of table speaker. Objects of this class serve as prototypes for rows in queries. */
  class Speakers(_tableTag: Tag) extends Table[SpeakerRow](_tableTag, "speaker") {
    def * = (id, sessionid, email, attributes, photo, lastmodified) <> (SpeakerRow.tupled, SpeakerRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(sessionid), Rep.Some(email), Rep.Some(attributes), photo, Rep.Some(lastmodified)).shaped.<>({r=>import r._; _1.map(_=> SpeakerRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(uuid) */
    val id: Rep[java.util.UUID] = column[java.util.UUID]("id")
    /** Database column sessionid SqlType(uuid) */
    val sessionid: Rep[java.util.UUID] = column[java.util.UUID]("sessionid")
    /** Database column email SqlType(varchar), Length(512,true) */
    val email: Rep[String] = column[String]("email", O.Length(512,varying=true))
    /** Database column attributes SqlType(jsonb), Length(2147483647,false) */
    val attributes: Rep[Json] = column[Json]("attributes")
    /** Database column photo SqlType(varchar), Length(1024,true), Default(None) */
    val photo: Rep[Option[String]] = column[Option[String]]("photo", O.Length(1024,varying=true), O.Default(None))
    /** Database column lastmodified SqlType(timestamptz) */
    val lastmodified: Rep[DateTime] = column[DateTime]("lastmodified")

    /** Primary key of Speaker (database name speaker_pk) */
    val pk = primaryKey("speaker_pk", (id, sessionid))

    /** Foreign key referencing Session (database name speaker_session_fk) */
    lazy val sessionFk = foreignKey("speaker_session_fk", sessionid, Sessions)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Speaker */
  lazy val Speakers = new TableQuery(tag => new Speakers(tag))
}
