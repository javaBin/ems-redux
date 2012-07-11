package no.java.ems

import javax.servlet.http.HttpServletRequest
import model.Entity
import no.java.ems.converters._
import java.net.URI
import no.java.http.URIBuilder
import io.Source
import unfiltered.response._
import unfiltered.request._
import no.java.unfiltered.{RequestURIBuilder, RequestContentDisposition, BaseURIBuilder}
import net.hamnaberg.json.collection._

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait EventResources extends ResourceHelper { this: Storage =>
  def handleEventList(request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val output = this.getEvents().map(eventToItem(baseUriBuilder))
        val href = baseUriBuilder.segments("events").build()
        CollectionJsonResponse(JsonCollection(href, Nil, output))
      }
      case req@POST(RequestContentType(CollectionJsonResponse.contentType)) => {
        withTemplate(req) {
          t => {
            val e = toEvent(None, t)
            this.saveEvent(e)
            NoContent
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleEvent(id: String, request: HttpRequest[HttpServletRequest]) = {
    val event = this.getEvent(id)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(event, request, (t: Template) => toEvent(Some(id), t), eventToItem(base))
  }

  def handleSessionList(eventId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) & Params(p) => {
        val href = baseUriBuilder.segments("events", eventId, "sessions").build()
        val sessions = p("title").headOption.map(t => getSessionsByTitle(eventId, t)).getOrElse(this.getSessions(eventId))
        val items = sessions.map(sessionToItem(baseUriBuilder))
        val coll = JsonCollection(href, Nil, items).
          addQuery(new Query(href, "search by-title", Some("By Title"), List(Property("title")))).
          addQuery(new Query(href, "search by-tags", Some("By Tags"), List(Property("tags"))))
        CollectionJsonResponse(coll)
      }
      case req@POST(RequestContentType(CollectionJsonResponse.contentType)) => {
        withTemplate(req) {
          t => {
            val session = toSession(eventId, None, t)
            this.saveSession(session)
            NoContent
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleSession(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest]) = {
    val session = this.getSession(eventId, sessionId)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(session, request, (t: Template) => toSession(eventId, Some(sessionId), t), sessionToItem(base))
  }

  private def getValidURIForPublish(eventId: String, u: URI) = {
    val segments = URIBuilder(u).path.map(_.seg)
    segments match {
      case "events" :: `eventId` :: "sessions" :: id :: Nil => Seq(id)
      case _ => Nil
    }
  }

  private def publishNow(eventId: String, list: URIList) {
    val sessions = list.list.flatMap(getValidURIForPublish(eventId, _))

    sessions.foreach(s => {
      val session = this.getSession(eventId, s)
      session.foreach(sess =>
        this.saveSession(sess.publish)
      )
    })
  }

  def publish(eventId: String, request: HttpRequest[HttpServletRequest]) = request match {
    case POST(RequestContentType("text/uri-list")) => {
      val list = URIList.parse(Source.fromInputStream(request.inputStream))
      if (list.isRight) {
        publishNow(eventId, list.right.get)
        NoContent
      }
      else {
        val e = list.left.get
        e.printStackTrace()
        BadRequest
      }
    }
    case POST(_)=> UnsupportedMediaType
    case _ => MethodNotAllowed
  }

  def handleSpeakers(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(builder) & RequestURIBuilder(requestURIBuilder) => {
        val session = this.getSession(eventId, sessionId)
        val items = session.toList.flatMap(sess => sess.speakers.map(speakerToItem(builder, eventId, sessionId)))
        CollectionJsonResponse(JsonCollection(requestURIBuilder.build(), Nil, items))
      }
    }
  }

  def handleSpeaker(eventId: String, sessionId: String, speakerId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & RequestURIBuilder(requestURIBuilder) & BaseURIBuilder(builder) => {
        val session = this.getSession(eventId, sessionId)
        val speaker = session.flatMap(_.speakers.find(_.contactId == speakerId)).map(speakerToItem(builder, eventId, sessionId))
        if (speaker.isDefined) {
          CollectionJsonResponse(JsonCollection(requestURIBuilder.build(), Nil, speaker.get))
        }
        else {
          NotFound
        }
      }
    }
  }

  def handleSpeakerPhoto(eventId: String, sessionId: String, contactId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case POST(_) & RequestContentType(ct) if (MIMEType.ImageAll.includes(MIMEType(ct).get)) => {
        request match {
          case RequestContentDisposition(cd) => {
            val session = this.getSession(eventId, sessionId)
            val speaker = session.flatMap(_.speakers.find(_.contactId == contactId))
            if (speaker.isDefined) {
              val binary = this.saveAttachment(StreamingAttachment(cd.filename.getOrElse(cd.filenameSTAR.get.filename), None, MIMEType(ct), request.inputStream))
              val updated = speaker.get.copy(photo = Some(binary))
              val updatedSession = session.get.addOrUpdateSpeaker(updated)
              this.saveSession(updatedSession)
              NoContent
            }
            else {
              NotFound
            }
          }
          case _ => {
            val builder = RequestURIBuilder.unapply(request).get
            BadRequest ~> CollectionJsonResponse(
              JsonCollection(
                builder.build(),
                ErrorMessage("Missing Content Disposition", None, Some("You need to add a Content-Disposition header."))
              )
            )
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case GET(_) & BaseURIBuilder(b) => {
        val session = this.getSession(eventId, sessionId)
        val image = session.flatMap(_.speakers.find(_.contactId == contactId)).flatMap(_.photo.map(i => b.segments("binary", i.id.get).build()))
        if (image.isDefined) Redirect(image.get.toString) else MethodNotAllowed
      }
      case _ => MethodNotAllowed
    }
  }

  def handleSessionAttachments(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & RequestURIBuilder(requestURIBuilder) => {
        val items = this.getSession(eventId, sessionId).map(_.attachments.map(attachmentToItem)).getOrElse(Nil)
        CollectionJsonResponse(JsonCollection(requestURIBuilder.build(), Nil, items))
      }

      case req@POST(RequestContentType(CollectionJsonResponse.contentType)) => {
        val sess = this.getSession(eventId, sessionId)
        sess match {
          case Some(s) => {
            withTemplate(req) {
              t => {
                val attachment = toAttachment(t)
                val updated = s.addAttachment(attachment)
                this.saveSession(updated)
                NoContent
              }
            }
          }
          case None => NotFound
        }
      }
      case req@POST(RequestContentType(ct)) & RequestURIBuilder(requestURIBuilder) => {
        val sess = this.getSession(eventId, sessionId)
        sess match {
          case Some(s) => {
            req match {
              case RequestContentDisposition(cd) & BaseURIBuilder(baseURIBuilder) => {
                val att = this.saveAttachment(StreamingAttachment(cd.filename.getOrElse(cd.filenameSTAR.get.filename), None, MIMEType(ct), req.inputStream))
                val attached = s.addAttachment(toURIAttachment(baseURIBuilder.segments("binary"), att))
                this.saveSession(attached)
                NoContent
              }
              case _ => {
                val href = requestURIBuilder.build()
                BadRequest ~> CollectionJsonResponse(JsonCollection(href, ErrorMessage("Wrong response", None, Some("Missing Content-Disposition header for binary data"))))
              }
            }
          }
          case None => NotFound
        }
      }
      case _ => MethodNotAllowed
    }
  }

  private def toURIAttachment(base: URIBuilder, attachment: Attachment with Entity) = {
    if (!attachment.id.isDefined) {
      throw new IllegalStateException("Tried to convert an unsaved Attachment; Failure")
    }
    URIAttachment(base.segments(attachment.id.get).build(), attachment.name, attachment.size, attachment.mediaType)
  }
}
