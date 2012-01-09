package no.java.ems

import unfiltered.request._
import unfiltered.response._
import unfiltered.request.Accepts
import unfiltered.filter.Plan
import org.joda.time.DateTime
import net.hamnaberg.json.collection._
import no.java.ems.converters._
import javax.servlet.http.HttpServletRequest
import Resources._
import java.io.OutputStream
import no.java.http.URIBuilder
import no.java.unfiltered.{ContentDisposition, RequestURIBuilder, RequestContentDisposition, BaseURIBuilder}

class Resources(storage: Storage) extends Plan {

  def intent = {
    case req@GET(Path(Seg("events" :: Nil))) => handleEventsList(req)
    case req@Path(Seg("events" :: id :: Nil)) => handleEvent(id, req)
    case req@GET(Path(Seg("events" :: eventId :: "sessions" :: Nil))) => handleSessionList(eventId, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: id :: Nil)) => handleSession(eventId, id, req)
    case req@Path(Seg("events" :: eventId :: "sessions" :: sessionId :: "attachments" :: Nil)) => handleAttachments(eventId, sessionId, req)
    case req@Path(Seg("binary" :: id :: Nil)) => handleAttachment(id, req)
  }

  def handleEventsList(request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val output = storage.getEvents().map(eventToItem(baseUriBuilder))
        val href = baseUriBuilder.segments("events").build()
        CollectionJsonResponse(JsonCollection(href, Nil, output))
      }
      case req@POST(RequestContentType(Resources.contentType)) => {
        withTemplate(req) {
          t => {
            val e = toEvent(None, t)
            storage.saveEvent(e)
            NoContent
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleEvent(id: String, request: HttpRequest[HttpServletRequest]) = {
    val event = storage.getEvent(id)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(event, request, (t: Template) => toEvent(Some(id), t), eventToItem(base))
  }


  def handleSessionList(eventId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val href = baseUriBuilder.segments("events", eventId, "sessions").build()
        val items = storage.getSessions(eventId).map(sessionToItem(baseUriBuilder))
        CollectionJsonResponse(JsonCollection(href, Nil, items))
      }
      case req@POST(RequestContentType(Resources.contentType)) => {
        withTemplate(req) {
          t => {
            val session = toSession(eventId, None, t)
            storage.saveSession(session)
            NoContent
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleSession(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest]) = {
    val session = storage.getSession(eventId, sessionId)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(session, request, (t: Template) => toSession(eventId, Some(sessionId), t), sessionToItem(base))
  }

  def handleAttachments(eventId: String, sessionId: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & RequestURIBuilder(requestURIBuilder) => {
        val items = storage.getSession(eventId, sessionId).map(_.attachments.map(attachmentToItem)).getOrElse(Nil)
        CollectionJsonResponse(JsonCollection(requestURIBuilder.build(), Nil, items))
      }

      case req@POST(RequestContentType(Resources.contentType)) => {
        val sess = storage.getSession(eventId, sessionId)
        sess match {
          case Some(s) => {
            withTemplate(req) {
              t => {
                val attachment = toAttachment(t)
                val updated = s.addAttachment(attachment)
                storage.saveSession(updated)
                NoContent
              }
            }
          }
          case None => NotFound
        }
      }
      case req@POST(RequestContentType(ct)) & RequestURIBuilder(requestURIBuilder) => {
        val sess = storage.getSession(eventId, sessionId)
        sess match {
          case Some(s) => {
            req match {
              case RequestContentDisposition(cd) & BaseURIBuilder(baseURIBuilder) => {
                val att = storage.saveAttachment(StreamingAttachment(cd.name, None, Some(MIMEType(ct)), req.inputStream))
                val attached = s.addAttachment(toURIAttachment(baseURIBuilder.segments("binary"), att))
                storage.saveSession(attached)
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

  def handleObject[T <: Entity](obj: Option[T], request: HttpRequest[HttpServletRequest], fromTemplate: (Template) => T, toItem: (T) => Item) = {
    request match {
      case GET(req) => {
        req match {
          case IfModifiedSince(date) if (obj.isDefined && obj.get.lastModified.toDate == date) => {
            NotModified
          }
          case _ => {
            obj.map(toItem).map(JsonCollection(_)).map(CollectionJsonResponse(_)).getOrElse(NotFound)
          }
        }
      }
      case req@PUT(RequestContentType(Resources.contentType)) => {
        req match {
          case IfUnmodifiedSince(date) if (obj.isDefined && obj.get.lastModified.toDate == date) => {
            withTemplate(req) {
              t => {
                val e = fromTemplate(t)
                storage.saveEntity(e)
                NoContent
              }
            }
          }
          case _ => PreconditionFailed
        }
      }
      case PUT(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleAttachment(id: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) => {
        storage.getAttachment(id) match {
          case Some(a) => AttachmentStreamer(a)
          case None => NotFound
        }
      }
      case DELETE(_) => {
        storage.removeAttachment(id)
        NoContent
      }
      case _ => MethodNotAllowed
    }
  }


  private def withTemplate[A](req: HttpRequest[HttpServletRequest])(f: (Template) => ResponseFunction[A]) = {
    val requestUriBuilder = RequestURIBuilder.unapply(req).get
    req match {
      case RequestContentType(Resources.contentType) => {
        val template = LiftJsonCollectionParser.parseTemplate(req.inputStream)
        template match {
          case Left(e) => BadRequest ~>
            CollectionJsonResponse(
              JsonCollection(
                requestUriBuilder.build(),
                ErrorMessage("Error with request", None, Option(e.getMessage))
              )
            )
          case Right(t) => {
            f(t)
          }
        }
      }
      case _ => UnsupportedMediaType
    }
  }
}

object Resources {
  val contentType = "application/vnd.collection+json"
}

object CollectionJsonResponse {

  import net.liftweb.json._

  def apply(coll: JsonCollection) = {
    new ComposeResponse[Any](ContentType(contentType) ~> ResponseString(compact(render(coll.toJson))))
  }
}

object AttachmentStreamer {
  def apply(attachment: Attachment) = {
    new ResponseStreamer {
      val buf = new Array[Byte](1024 * 8)

      def stream(os: OutputStream) {
        val length = attachment.data.read(buf)
        os.write(buf, 0, length)
      }
    } ~> ContentDisposition(attachment.name).toResponseHeader
  }
}

object AcceptCollectionJson extends Accepts.Accepting {
  val contentType = Resources.contentType
  val ext = "json"

  override def unapply[T](r: HttpRequest[T]) = Accepts.Json.unapply(r) orElse super.unapply(r)
}

object Main extends App {
  val storage = MemoryStorage()
  val resources = new Resources(storage)

  def populate(storage: Storage) {
    val event = storage.saveEvent(Event(Some("1"), "JavaZone 2011", new DateTime(), new DateTime()))
    val sessions = List(Session(event.id.get, "Session 1", Format.Presentation, Vector()).copy(Some("1")), Session(event.id.get, "Session 2", Format.Presentation, Vector()).copy(Some("2")))
    for (s <- sessions) storage.saveSession(s)
    println(event)
  }

  populate(storage)

  unfiltered.jetty.Http(8080).plan(resources).run()
}