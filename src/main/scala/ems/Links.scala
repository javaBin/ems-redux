package ems

import linx._

object Links {
  val Events = Root / "events"
  val Event  = Events / 'eventId
  val Slots = Event / "slots"
  val Slot = Slots / 'slotId
  val Rooms = Event / "rooms"  
  val Room = Rooms / 'roomId
  val Sessions = Event / "sessions"
  val SessionsTags = Sessions / "tags" 
  val Session = Sessions / 'sessionId
  val SessionSlot = Session / "slot"
  val SessionRoom = Session / "room"
  val SessionAttachments = Session / "attachments"
  val Speakers = Session / "speakers"
  val Speaker = Speakers / 'speakerId
  val SpeakerPhoto = Speaker / "photo"

  val Binary = Root / "binary" / 'id


  def resolve(base: URI, path: String) = {
    URIBuilder(base).path(path).build()
  } 
}
