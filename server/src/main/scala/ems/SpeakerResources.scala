package ems

import javax.servlet.http.HttpServletRequest
import model.Speaker
import ems.converters._
import security.User
import unfiltered.response._
import unfiltered.request._
import no.java.unfiltered.{RequestURIBuilder, RequestContentDisposition, BaseURIBuilder}
import net.hamnaberg.json.collection._

trait SpeakerResources extends ResourceHelper {

  def handleSpeakers(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    request match {
      case GET(_) & BaseURIBuilder(builder) & RequestURIBuilder(requestURIBuilder) => {
        val session = storage.getSession(eventId, sessionId)
        val items = session.toList.flatMap(sess => sess.speakers.map(speakerToItem(builder, eventId, sessionId)))
        CollectionJsonResponse(JsonCollection(requestURIBuilder.build(), Nil, items))
      }
      case POST(_) & BaseURIBuilder(builder) => {
        authenticated(request, user) {
          case _ => {
            val optionalSession = storage.getSession(eventId, sessionId)
            if (!optionalSession.isDefined) {
              NotFound
            }
            else {
              withTemplate(request) {
                t =>
                  val session = optionalSession.get
                  val speaker = toSpeaker(t)
                  val exists = session.speakers.exists(_.email == speaker.email)
                  if (exists) {
                    BadRequest ~> ResponseString("There already exists a speaker with this email")
                  }
                  else {
                    storage.saveSpeaker(eventId, sessionId, speaker).fold(
                      ex => InternalServerError ~> ResponseString(ex.getMessage),
                      saved => {
                        val href = builder.segments("events", eventId, "sessions", sessionId, "speakers", saved.id.get).build()
                        Created ~> Location(href.toString)
                      }
                    )
                  }
              }
            }
          }
        }
      }
      case _ => MethodNotAllowed
    }
  }

  def handleSpeaker(eventId: String, sessionId: String, speakerId: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    val speaker = storage.getSpeaker(eventId, sessionId, speakerId)
    val base = BaseURIBuilder.getBuilder(request)
    handleObject(
      speaker,
      request,
      (t: Template) => toSpeaker(t, Some(speakerId)),
      storage.saveSpeaker(eventId, sessionId, _: Speaker),
      speakerToItem(base, eventId, sessionId)
    )(identity)
  }

  def handleSpeakerPhoto(eventId: String, sessionId: String, speakerId: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    request match {
      case POST(_) => {
        authenticated(request, user) {
          case req@RequestContentType(ct) & BaseURIBuilder(base) if (MIMEType.ImageAll.includes(MIMEType(ct).get)) => {
            request match {
              case RequestContentDisposition(cd) => {
                val speaker = storage.getSpeaker(eventId, sessionId, speakerId)
                speaker.map{ sp =>
                  sp.photo.foreach(ph => storage.binary.removeAttachment(ph.id.get))

                  val binary = storage.binary.saveAttachment(StreamingAttachment(cd.filename.orElse(cd.filenameSTAR.map(_.filename)).get, None, MIMEType(ct), request.inputStream))
                  storage.updateSpeakerWithPhoto(eventId, sessionId, speakerId, binary).fold(ex =>
                    InternalServerError ~> ResponseString(ex.getMessage),
                    _ => Created ~> Location(base.segments("binary", binary.id.get).toString())
                  )

                }.getOrElse(NotFound)
              }
              case _ => {
                val builder = RequestURIBuilder.unapply(request).get
                BadRequest ~> CollectionJsonResponse(
                  JsonCollection(
                    builder.build(),
                    Error("Missing Content Disposition", None, Some("You need to add a Content-Disposition header."))
                  )
                )
              }
            }
          }
          case _ => UnsupportedMediaType
        }
      }
      case GET(_) & BaseURIBuilder(b) => {
        val speaker = storage.getSpeaker(eventId, sessionId, speakerId)
        speaker.flatMap(_.photo.map(i => Redirect(b.segments("binary", i.id.get).toString()))).getOrElse(NotFound)
      }
      case _ => MethodNotAllowed
    }
  }
}
