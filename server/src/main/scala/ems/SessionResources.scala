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
import unfilteredx._
import net.hamnaberg.json.collection._
import ems.config.Config
import ems.cj.{ValueOptions, ValueOption}

trait SessionResources extends ResourceHelper {

  def handleSessionList(eventId: String)(implicit u: User) = {
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
    val post = createObject(t => toSession(eventId, None, t), storage.saveSession, (s : Session) => Seq("events", eventId, "sessions", s.id.get))

    get | post
  }

  def handleSession(eventId: String, sessionId: String)(implicit u: User) = for {
    base <- baseURIBuilder
    a <- handleObject(storage.getSession(eventId, sessionId), (t: Template) => toSession(eventId, Some(sessionId), t), storage.saveSession, sessionToItem(base)) {
      c => c.addQuery(Query(URIBuilder(c.href.get).segments("speakers").build(), "speaker by-email", List(
        ValueProperty("email")
      ), Some("Speaker by Email")))
    }
  } yield a

  def handleSessionTags(eventId: String, sessionId: String)(implicit u: User) =
    handleSessionForm(eventId, sessionId, "tag", (tags, s) => s.withTags(tags.map(Tag).toSet[Tag]))

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

    val post = handleSessionForm(eventId, sessionId, "slot", (slots, s) => slots.headOption.flatMap{ slotString =>
      val id = URIBuilder(slotString).path.last.seg
      storage.getSlot(eventId, id).map(slot => s.withSlot(slot))
    }.getOrElse(s))

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

    val post = handleSessionForm(eventId, sessionId, "room", (rooms, s) => rooms.headOption.flatMap{ r =>
      val id = URIBuilder(r).path.last.seg
      storage.getRoom(eventId, id).map(room => s.withRoom(room))
    }.getOrElse(s))

    get | post
  }

  private def handleSessionForm(eventId: String, sessionId: String, name: String, update: (Seq[String], Session) => Session)(implicit u: User) = for {
    _ <- POST
    _ <- authenticated(u)
    href <- requestURI
    base <- baseURIBuilder
    _ <- contentType("application/x-www-form-urlencoded")
    a <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
    _ <- ifUnmodifiedSince(a.lastModified)
    items <- parameterValues(name)
  } yield {
    storage.saveSession(update(items, a)).fold(
      ex => InternalServerError ~> ResponseString(ex.getMessage),
      s => DateResponseHeader("Last-Modified", s.lastModified.withMillisOfSecond(0)) ~> ContentLocation(href.toString) ~> CollectionJsonResponse(JsonCollection(href, Nil, sessionToItem(base)(u)(s)))
    )
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

  def publish(eventId: String)(implicit user: User) = for {
    _ <- autocommit(POST)
    _ <- authenticated(user)
    _ <- contentType("text/uri-list")
    is <- inputStream
    res <- URIList.parse(Source.fromInputStream(is)).fold(
      ex => failure(BadRequest),
      list => success(list)
    )
  } yield {
    publishNow(eventId, res)
    NoContent
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
