package ems

import javax.servlet.http.HttpServletRequest
import model.Entity
import ems.converters._
import java.net.URI
import no.java.util.URIBuilder
import security.User
import io.Source
import unfiltered.response._
import unfiltered.request._
import no.java.unfiltered.{RequestURIBuilder, RequestContentDisposition, BaseURIBuilder}
import net.hamnaberg.json.collection._
import ems.config.Config

trait SessionResources extends ResourceHelper {

  def handleSessionList(eventId: String, request: HttpRequest[HttpServletRequest])(implicit u: User) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) & Params(p) => {
        val href = baseUriBuilder.segments("events", eventId, "sessions").build()
        p("slug").headOption match {
          case Some(s) => storage.getSessionsBySlug(eventId, s).headOption.map(sess => Found ~> Location(URIBuilder(href).segments(sess.id.get).toString)).getOrElse(NotFound)
          case None => {
            val sessions = storage.getSessions(eventId)(u)
            val filtered = Some(u).filter(_.authenticated).map(_ => sessions).getOrElse(sessions.filter(_.published))
            val items = filtered.map(sessionToItem(baseUriBuilder))
            val coll = JsonCollection(href, Nil, items, List(
              Query(href, "session by-title", Some("By Title"), List(ValueProperty("title"))),
              Query(href, "session by-slug", Some("By Slug"), List(ValueProperty("slug"))),
              Query(href, "session by-tags", Some("By Tags"), List(ValueProperty("tags")))
            ))
            CacheControl("max-age" + Config.cache.sessions) ~>  CollectionJsonResponse(coll)
          }
        }
      }
      case POST(RequestContentType(CollectionJsonResponse.contentType)) & BaseURIBuilder(baseUriBuilder) => {
        authenticated(request, u) { case req =>
          withTemplate(req) {
            t => {
              val session = toSession(eventId, None, t)
              storage.saveSession(session).fold(
                ex => InternalServerError ~> ResponseString(ex.getMessage),
                saved => {
                  val id = saved.id.get
                  val href = baseUriBuilder.segments("events", eventId, "sessions", id).build()
                  Created ~> Location(href.toString)
                }
              )
            }
          }

        }
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleSession(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest])(implicit u: User) = {
    val session = storage.getSession(eventId, sessionId)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(session, request, (t: Template) => toSession(eventId, Some(sessionId), t), storage.saveSession, sessionToItem(base)){
      c => c.addQuery(Query(URIBuilder(c.href).segments("speakers").build(), "speaker by-email", Some("Speaker by Email"), List(
        ValueProperty("email")
      )))
    }
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
      val session = storage.getSession(eventId, s)
      session.foreach(sess =>
        storage.saveSession(sess.publish)
      )
    })
  }

  def publish(eventId: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = request match {
    case POST(_) => {
      authenticated(request, user) {
        case RequestContentType("text/uri-list") =>
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
        case _ => UnsupportedMediaType
      }
    }
    case _ => MethodNotAllowed
  }

  def handleSessionAttachments(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest])(implicit user: User) = {
    request match {
      case GET(_) & RequestURIBuilder(requestURIBuilder) & BaseURIBuilder(baseURIBuilder) => {
        val items = storage.getSession(eventId, sessionId).map(_.attachments.map(attachmentToItem(baseURIBuilder))).getOrElse(Nil)
        CollectionJsonResponse(JsonCollection(requestURIBuilder.build(), Nil, items))
      }

      case POST(_) => {
        authenticated(request, user) {
          case req@RequestContentType(CollectionJsonResponse.contentType) =>
            val sess = storage.getSession(eventId, sessionId)
            sess match {
              case Some(s) => {
                withTemplate(req) {
                  t => {
                    val attachment = toAttachment(t)
                    storage.saveAttachment(eventId, sessionId, attachment).fold(
                      ex => InternalServerError ~> ResponseString(ex.getMessage),
                      _ => NoContent
                    )
                  }
                }
              }
              case None => NotFound
            }
          case req@RequestContentType(ct) & RequestURIBuilder(requestURIBuilder) => {
            val sess = storage.getSession(eventId, sessionId)
            sess match {
              case Some(s) => {
                req match {
                  case RequestContentDisposition(cd) & BaseURIBuilder(baseURIBuilder) => {
                    val att = storage.binary.saveAttachment(StreamingAttachment(cd.filename.getOrElse(cd.filenameSTAR.get.filename), None, MIMEType(ct), req.inputStream))
                    storage.saveAttachment(eventId, sessionId, toURIAttachment(baseURIBuilder.segments("binary"), att)).fold(
                      ex => InternalServerError ~> ResponseString(ex.getMessage),
                      _ => NoContent
                    )
                  }
                  case _ => {
                    val href = requestURIBuilder.build()
                    BadRequest ~> CollectionJsonResponse(JsonCollection(href, Error("Wrong response", None, Some("Missing Content-Disposition header for binary data"))))
                  }
                }
              }
              case None => NotFound
            }

          }
        }
      }
      case _ => MethodNotAllowed
    }
  }

  private def toURIAttachment(base: URIBuilder, attachment: Attachment with Entity[Attachment]) = {
    if (!attachment.id.isDefined) {
      throw new IllegalStateException("Tried to convert an unsaved Attachment; Failure")
    }
    URIAttachment(None, base.segments(attachment.id.get).build(), attachment.name, attachment.size, attachment.mediaType)
  }
}
