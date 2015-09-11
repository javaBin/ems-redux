package ems

import model.Speaker
import ems.converters._
import security.User
import unfiltered.response._
import unfiltered.request._
import unfiltered.directives._
import Directives._
import net.hamnaberg.json.collection._

trait SpeakerResources extends ResourceHelper {

  def handleSpeakers(eventId: UUID, sessionId: UUID)(implicit user: User) = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      href <- requestURI
      session <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
    } yield {
      val speakers = storage.getSpeakers(sessionId)
      val items = speakers.map(speakerToItem(base, eventId, sessionId)).toList
      CollectionJsonResponse(JsonCollection(href, Nil, items, Nil, Some(makeTemplate("name", "email", "bio", "zip-code", "tags"))))
    }

    val post = for {
      _ <- POST
      _ <- authenticated(user)
      base <- baseURIBuilder
      session <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
      either <- withTemplate(t => toSpeaker(t, None))
      speaker <- either
    } yield {
      val speakers = storage.getSpeakers(sessionId)
      val exists = speakers.exists(_.email == speaker.email)
      if (exists) {
        BadRequest ~> ResponseString("There already exists a speaker with this email")
      }
      else {
        storage.saveSpeaker(sessionId, speaker).fold(
          ex => InternalServerError ~> ResponseString(ex.getMessage),
          saved => {
            val href = base.segments("events", eventId, "sessions", sessionId, "speakers", saved.id.get).build()
            Created ~> Location(href.toString)
          }
        )
      }
    }
    get | post
  }

  def handleSpeaker(eventId: UUID, sessionId: UUID, speakerId: UUID)(implicit user: User) = {
    val speaker = storage.getSpeaker(sessionId, speakerId)
    for {
      base <- baseURIBuilder
      res <- handleObject(
         speaker,
         (t: Template) => toSpeaker(t, Some(speakerId)),
         storage.saveSpeaker(sessionId, _: Speaker),
         speakerToItem(base, eventId, sessionId),
         Some((_: Speaker) => storage.removeSpeaker(sessionId, speakerId))
       )(identity)
    } yield {
      res
    }
  }

  def handleSpeakerPhoto(eventId: UUID, sessionId: UUID, speakerId: UUID)(implicit user: User) = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      speaker <- getOrElse(storage.getSpeaker(sessionId, speakerId), NotFound)
    } yield {
      speaker.photo.map(i => Redirect(base.segments("binary", i.id.get).toString())).getOrElse(NotFound)
    }

    val imageType = when {
      case RequestContentType(ct) if MIMEType.ImageAll.includes(MIMEType(ct).get) => ct
    }.orElse(UnsupportedMediaType)


    val post = for {
      _ <- POST
      ct <- imageType
      cd <- contentDisposition
      base <- baseURIBuilder
      speaker <- getOrElse(storage.getSpeaker(sessionId, speakerId), NotFound)
      is <- inputStream
    } yield {
      speaker.photo.foreach(ph => storage.binary.removeAttachment(ph.id.get))
      val binary = storage.binary.saveAttachment(StreamingAttachment(cd.filename.orElse(cd.filenameSTAR.map(_.filename)).get, None, MIMEType(ct), is))
      storage.updateSpeakerWithPhoto(sessionId, speakerId, binary).fold(
        ex => InternalServerError ~> ResponseString(ex.getMessage),
        _ => Created ~> Location(base.segments("binary", binary.id.get).toString())
      )
    }

    get | post
  }
}
