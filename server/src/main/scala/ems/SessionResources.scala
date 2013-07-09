package ems

import model._
import ems.converters._
import java.net.URI
import util.URIBuilder
import security.User
import io.Source
import unfiltered.response._
import unfiltered.request._
import unfiltered.directives._
import Directives._
import net.hamnaberg.json.collection._
import ems.config.Config
import ems.cj.{ValueOptions, ValueOption}
import javax.servlet.http.HttpServletRequest
import java.io.OutputStream

trait SessionResources extends ResourceHelper {

  def handleSessionList(eventId: String)(implicit u: User): Directive[HttpServletRequest, Any, ResponseFunction[Any]] = {
    val get = for {
      _ <- GET
      params <- queryParams
      rb <- baseURIBuilder
    } yield {
      val href = rb.segments("events", eventId, "sessions").build()
      params("slug").headOption match {
        case Some(s) => storage.getSessionsBySlug(eventId, s).headOption.map(sess => Found ~> Location(URIBuilder(href).segments(sess.id.get).toString)).getOrElse(NotFound)
        case None => {
          val sessions = storage.getSessions(eventId)(u)
          val filtered = Some(u).filter(_.authenticated).map(_ => sessions).getOrElse(sessions.filter(_.published))
          val items = filtered.map(sessionToItem(rb))
          val template = makeTemplate("title", "summary", "body", "outline", "audience", "equipment", "tags", "keywords").
            addProperty(ValueProperty("lang").apply(ValueOptions, List(ValueOption("no"), ValueOption("en")))).
            addProperty(ValueProperty("format").apply(ValueOptions, Format.values.map(f => ValueOption(f.name)))).
            addProperty(ValueProperty("level").apply(ValueOptions, Level.values.map(l => ValueOption(l.name))))
          val coll = JsonCollection(href, Nil, items, List(
            Query(href, "session by-slug", List(ValueProperty("slug")), Some("By Slug")),
            Query(href, "session by-tags", List(ValueProperty("tags")), Some("By Tags"))
          )).withTemplate(template)
          CacheControl("max-age=" + Config.cache.sessions) ~>  CollectionJsonResponse(coll)
        }
      }
    }
    val newSession = createObject(t => toSession(eventId, None, t), storage.saveSession, (s : Session) => Seq("events", eventId, "sessions", s.id.get))

    val post = for {
      _ <- POST
      ct <- when{case RequestContentType(ct) => ct}.orElse(BadRequest ~> ResponseString("Missing Content-Type header"))
      res <- if (ct == "text/uri-list") publish(eventId) else newSession
    } yield res

    get | post
  }

  def handleAllTags(eventId: String)(implicit u: User) = for {
    _ <- GET
    _ <- authenticated(u)
  } yield {
    val tags = storage.getSessions(eventId)(u).flatMap(_.tags.map(_.name).toSeq).toSet[String]
    JsonContent ~> new ResponseStreamer {
      import org.json4s._
      import org.json4s.native.JsonMethods._
      def stream(os: OutputStream) {
        os.write(compact(render(JObject(JField("tags", JArray(tags.map(JString).toList))))).getBytes("UTF-8"))
      }
    }
  }

  def handleSession(eventId: String, sessionId: String)(implicit u: User) = for {
    base <- baseURIBuilder
    a <- handleObject(storage.getSession(eventId, sessionId), (t: Template) => {
      val updated = toSession(eventId, Some(sessionId), t)
      storage.getSession(eventId, sessionId).map(s => updated.copy(tags = s.tags ++ updated.tags)).getOrElse(updated)
    }, storage.saveSession, sessionToItem(base)) {
      c =>
        val template = makeTemplate("title", "summary", "body", "outline", "audience", "equipment", "keywords").
          addProperty(ValueProperty("lang").apply(ValueOptions, List(ValueOption("no"), ValueOption("en")))).
          addProperty(ValueProperty("format").apply(ValueOptions, Format.values.map(f => ValueOption(f.name)))).
          addProperty(ValueProperty("state").apply(ValueOptions, State.values.map(s => ValueOption(s.name)))).
          addProperty(ValueProperty("level").apply(ValueOptions, Level.values.map(l => ValueOption(l.name))))
        c.addQuery(Query(URIBuilder(c.href).segments("speakers").build(), "speaker by-email", List(
        ValueProperty("email")
      ), Some("Speaker by Email"))).withTemplate(template)
    }
  } yield a

  def handleSessionTags(eventId: String, sessionId: String)(implicit u: User) =
    handleSessionForm(eventId, sessionId, "tag", (tags, s) => {
      val updated = s.withTags(tags.map(Tag).toSet[Tag])
      storage.saveSession(updated)
    })

  def handleSessionSlot(eventId: String, sessionId: String)(implicit u: User) = {
    val get = for {
      _ <- GET
      a <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
      base <- baseURIBuilder
    } yield {
      HtmlContent ~> Html5(
        <html>
          <head><title>Rooms</title></head>
          <body>
            <form method="post">
              <select name="slot" id="slot">
                {
                storage.getSlots(eventId).map{s =>
                  <option value={base.segments("events", eventId, "slots", s.id.get).toString}>{formatSlot(s)}</option>
                }
                }
              </select>
            </form>
          </body>
        </html>
      )
    }

    val post = handleSessionForm(eventId, sessionId, "slot", (slots, s) => {
      val slot = slots.headOption.flatMap{ slot =>
        val id = URIBuilder(slot).path.last.seg
        storage.getSlot(eventId, id)
      }
      slot.map(s => storage.saveSlotInSession(eventId, sessionId, s)).getOrElse(Left(new IllegalArgumentException("Not found")))
    })

    get | post
  }

  def handleSessionRoom(eventId: String, sessionId: String)(implicit u: User) = {
    val get = for {
      _ <- GET
      a <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
      base <- baseURIBuilder
    } yield {
      HtmlContent ~> Html5(
        <html>
          <head><title>Rooms</title></head>
          <body>
            <form method="post">
              <select name="room" id="room">
                {
                  storage.getRooms(eventId).map{r =>
                     <option value={base.segments("events", eventId, "rooms", r.id.get).toString}>{r.name}</option>
                  }
                }
              </select>
            </form>
          </body>
        </html>
      )
    }

    val post = handleSessionForm(eventId, sessionId, "room", (rooms, s) => {
      val room = rooms.headOption.flatMap{ r =>
        val id = URIBuilder(r).path.last.seg
        storage.getRoom(eventId, id)
      }
      room.map(r => storage.saveRoomInSession(eventId, sessionId, r)).getOrElse(Left(new IllegalArgumentException("Not found")))
    })

    get | post
  }

  private def handleSessionForm(eventId: String, sessionId: String, name: String, update: (Seq[String], Session) => Either[Exception, Session])(implicit u: User) = for {
    _ <- POST
    _ <- authenticated(u)
    href <- requestURI
    base <- baseURIBuilder
    _ <- contentType("application/x-www-form-urlencoded")
    a <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
    _ <- ifUnmodifiedSince(a.lastModified)
    items <- parameterValues(name)
  } yield {
    val sessionHref = base.segments("events", eventId, "sessions", sessionId).build()
    update(items, a).fold(
      ex => InternalServerError ~> ResponseString(ex.getMessage),
      s => SeeOther ~> Location(sessionHref.toString)
    )
  }

  private def getValidURIForPublish(eventId: String, u: URI) = {
    val path = u.getPath
    val index = path.indexOf(s"/events/$eventId/sessions/")
    if (index > 0) Seq(path.substring(path.lastIndexOf("/") + 1)) else Nil
  }

  private def publishNow(eventId: String, list: URIList) = {
    val sessions = list.list.flatMap(getValidURIForPublish(eventId, _))
    storage.publishSessions(eventId, sessions).fold(
      ex => BadRequest,
      _ => NoContent
    )
  }

  private def publish(eventId: String)(implicit user: User) = for {
    _ <- POST
    _ <- authenticated(user)
    _ <- contentType("text/uri-list")
    is <- inputStream
    res <- URIList.parse(Source.fromInputStream(is)).fold(
      ex => failure(BadRequest),
      list => success(list)
    )
  } yield {
    publishNow(eventId, res)
  }

  def handleSessionAttachments(eventId: String, sessionId: String)(implicit user: User) = {
    val get = for {
      _ <- GET
      href <- requestURI
      base <- baseURIBuilder
      obj <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
    } yield {
      val items = obj.attachments.map(attachmentToItem(base))
      CollectionJsonResponse(JsonCollection(href, Nil, items.toList))
    }

    val cj = for {
      _ <- contentType(CollectionJsonResponse.contentType)
      either <- withTemplate(t => toAttachment(t))
      res <- either.right.flatMap(a => storage.saveAttachment(eventId, sessionId, a)) fold(
        ex => failure(InternalServerError ~> ResponseString(ex.getMessage)),
        _ => success(NoContent)
        )
    } yield res

    val binary = for {
      ct <- commit(when{case RequestContentType(ct) => ct}.orElse(BadRequest))
      base <- baseURIBuilder
      href <- requestURI
      cd <- contentDisposition
      is <- inputStream
    } yield {
      val att = storage.binary.saveAttachment(StreamingAttachment(cd.filename.getOrElse(cd.filenameSTAR.get.filename), None, MIMEType(ct), is))
      storage.saveAttachment(eventId, sessionId, toURIAttachment(base.segments("binary"), att)).fold(
        ex => InternalServerError ~> ResponseString(ex.getMessage),
        _ => NoContent
      )
    }

    val post = for {
      _ <- POST
      _ <- authenticated(user)
      s <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
      res <- cj | binary
    } yield res

    get | post
  }

  private def toURIAttachment(base: URIBuilder, attachment: Attachment with Entity[Attachment]) = {
    if (!attachment.id.isDefined) {
      throw new IllegalStateException("Tried to convert an unsaved Attachment; Failure")
    }
    URIAttachment(None, base.segments(attachment.id.get).build(), attachment.name, attachment.size, attachment.mediaType)
  }
}
