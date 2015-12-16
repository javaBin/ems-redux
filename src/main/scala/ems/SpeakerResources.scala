package ems

import java.io.InputStream
import java.net.URI

import ems.util.URIBuilder
import model.Speaker
import ems.converters._
import security.User
import unfiltered.response._
import unfiltered.request._
import net.hamnaberg.json.collection._
import unfilteredx.ContentDisposition
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SpeakerResources extends ResourceHelper {
  import Directives._
  import ops._

  def handleSpeakers(eventId: UUID, sessionId: UUID)(implicit user: User): ResponseDirective = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      href <- requestURI
      session <- getOrElseF(storage.getSession(eventId, sessionId), NotFound)
      speakers <- storage.getSpeakers(sessionId).successValue
    } yield {
      val items = speakers.map(speakerToItem(base, eventId, sessionId)).toList
      CollectionJsonResponse(JsonCollection(href, Nil, items, Nil, Some(makeTemplate("name", "email", "bio", "zip-code", "tags"))))
    }

    def postRes(speakers: Vector[Speaker], speaker: Speaker, base: URIBuilder) = {
      val exists = speakers.exists(_.email == speaker.email)
      if (exists) {
        Future.successful(BadRequest ~> ResponseString("There already exists a speaker with this email"))
      }
      else {
        storage.saveSpeaker(sessionId, speaker).map(saved => {
          val href = base.segments("events", eventId, "sessions", sessionId, "speakers", saved.id.get).build()
          Created ~> Location(href.toString)
        })
      }
    }.successValue

    val post = for {
      _ <- POST
      _ <- authenticated(user)
      base <- baseURIBuilder
      session <- getOrElseF(storage.getSession(eventId, sessionId), NotFound)
      either <- withTemplate(t => toSpeaker(t, None))
      speaker <- fromEither(either)
      speakers <- storage.getSpeakers(sessionId).successValue
      res <- postRes(speakers, speaker, base)
    } yield {
      res
    }
    get | post
  }

  def handleSpeaker(eventId: UUID, sessionId: UUID, speakerId: UUID)(implicit user: User): ResponseDirective = {
    for {
      base <- baseURIBuilder
      speaker <- getOrElseF(storage.getSpeaker(sessionId, speakerId), NotFound)
      res <- handleObject(
         Future.successful(Some(speaker)),
         (t: Template) => toSpeaker(t, Some(speakerId)),
         storage.saveSpeaker(sessionId, _: Speaker),
         speakerToItem(base, eventId, sessionId),
         Some((_: Speaker) => storage.removeSpeaker(sessionId, speakerId))
       )(identity)
    } yield {
      res
    }
  }

  def handleSpeakerPhoto(eventId: UUID, sessionId: UUID, speakerId: UUID)(implicit user: User): ResponseDirective = {
    val get = for {
      _ <- GET
      base <- baseURIBuilder
      speaker <- getOrElseF(storage.getSpeaker(sessionId, speakerId), NotFound)
    } yield {
      speaker.photo.map(i => Redirect(base.segments("binary", i.id.get).toString())).getOrElse(NotFound)
    }

    val imageType = when {
      case RequestContentType(ct) if MIMEType.ImageAll.includes(MIMEType(ct).get) => ct
    }.orElse(UnsupportedMediaType)

    def postPhoto(cd: ContentDisposition, ct: String, speaker: Speaker, base: URIBuilder, is: InputStream) = {
      speaker.photo.foreach(ph => storage.binary.removeAttachment(ph.id.get))
      val binary = storage.binary.saveAttachment(StreamingAttachment(cd.filename.orElse(cd.filenameSTAR.map(_.filename)).get, None, MIMEType(ct), is))
      storage.updateSpeakerWithPhoto(sessionId, speakerId, URI.create(binary.id.get)).map(
        _ => Created ~> Location(base.segments("binary", binary.id.get).toString())
      )
    }.successValue

    val post = for {
      _ <- POST
      ct <- imageType
      cd <- contentDisposition
      base <- baseURIBuilder
      speaker <- getOrElseF(storage.getSpeaker(sessionId, speakerId), NotFound)
      is <- inputStream
      res <- postPhoto(cd, ct, speaker, base, is)
    } yield {
      res
    }

    get | post
  }
}
