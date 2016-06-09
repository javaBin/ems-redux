package ems

import linx._
import java.net.URI
import util.URIBuilder

object Links {
  val Events = Root / "events"
  val Event  = Events / 'eventId
  val Slots = Event / "slots"
  val Slot = Slots / 'slotId
  val SlotChildren = Slot / "children"
  val Rooms = Event / "rooms"
  val Room = Rooms / 'roomId
  val Sessions = Event / "sessions"
  val SessionsChangelog = Sessions / "changelog"
  val SessionsTags = Sessions / "tags"
  val Session = Sessions / 'sessionId
  val SessionSlot = Session / "slot"
  val SessionRoom = Session / "room"
  val SessionAttachments = Session / "attachments"
  val Speakers = Session / "speakers"
  val Speaker = Speakers / 'speakerId
  val SpeakerPhoto = Speaker / "photo"

  val Binary = Root / "binary" / 'id

  val GraphQl = Root / "graphql"
  val GraphQlSchema = Root / "graphql-schema"


  def resolve(base: URI, path: String) = {
    URIBuilder(base).path(path).build()
  } 
}
