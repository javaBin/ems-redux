package ems

import model._
import ems.converters._
import java.net.URI
import ems.util.{RFC3339, URIBuilder}
import security.User
import io.Source
import unfiltered.response._
import unfilteredx._
import unfiltered.request._
import unfiltered.directives._
import Directives._
import net.hamnaberg.json.collection._
import ems.cj.{ValueOptions, ValueOption}
import java.io.OutputStream

trait SessionResources extends ResourceHelper {

  def handleSessionList(eventId: UUID)(implicit u: User): ResponseDirective = {
    val get = for {
      _ <- GET
      params <- queryParams
      rb <- baseURIBuilder
    } yield {
      val href = rb.segments("events", eventId, "sessions").build()
      params("slug").headOption match {
        case Some(s) => storage.getSessionsBySlug(eventId, s).headOption.map(sess => Found ~> Location(URIBuilder(href).segments(sess.id.get).toString)).getOrElse(NotFound)
        case None => {
          val sessions = storage.getSessionsEnriched(eventId)(u)
          val items = sessions.map(enrichedSessionToItem(rb))
          val template = makeTemplate("title", "summary", "body", "outline", "audience", "equipment", "tags", "keywords", "published").
            addProperty(ValueProperty("lang").apply(ValueOptions, List(ValueOption("no"), ValueOption("en")))).
            addProperty(ValueProperty("format").apply(ValueOptions, Format.values.map(f => ValueOption(f.name)))).
            addProperty(ValueProperty("level").apply(ValueOptions, Level.values.map(l => ValueOption(l.name))))
          val coll = JsonCollection(href, Nil, items.toList, List(
            Query(href, "session by-slug", List(ValueProperty("slug")), Some("By Slug")),
            Query(href, "session by-tags", List(ValueProperty("tags")), Some("By Tags"))
          )).withTemplate(template)
          CollectionJsonResponse(coll)
        }
      }
    }
    val newSession = for {
      base <- baseURIBuilder
      res <- createObject(
        t => toSession(eventId, None, t),
        storage.saveSession,
        (s: Session) => Seq("events", eventId, "sessions", s.id.get),
        (s: Session) => Seq(LinkHeader(base.segments("events", eventId, "sessions", s.id.get, "speakers").build(), "speaker collection"))
      )
    } yield {
      res
    }

    val post = for {
      _ <- POST
      ct <- when{case RequestContentType(ct) => ct}.orElse(BadRequest ~> ResponseString("Missing Content-Type header"))
      res <- if (ct == "text/uri-list") publish(eventId) else newSession
    } yield res

    get | post
  }

  def handleAllTags(eventId: UUID)(implicit u: User) = for {
    _ <- GET
    _ <- authenticated(u)
  } yield {
    val tags = storage.getSessions(eventId)(u).flatMap(_.abs.tags.map(_.name).toSeq).toSet[String]
    JsonContent ~> new ResponseStreamer {
      import org.json4s._
      import org.json4s.native.JsonMethods._
      def stream(os: OutputStream) {
        os.write(compact(render(JObject(JField("tags", JArray(tags.map(JString).toList))))).getBytes("UTF-8"))
      }
    }
  }

  def handleSessionAndForms(eventId: UUID, sessionId: UUID)(implicit u: User) = {
    val form = for {
      _ <- POST
      _ <- authenticated(u)
      ct <- contentType("application/x-www-form-urlencoded")
      params <- when {
        case Params(p) => p
      }.orElse(BadRequest)
      _ <- commit
      session <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
      _ <- ifUnmodifiedSince(session.lastModified)
    } yield {
      val tags = params.get("tag").filterNot(_.isEmpty)
      val slot = params.get("slot").flatMap(_.headOption).flatMap{ slot =>
        val id = UUIDFromString(URIBuilder(slot).path.last.seg)
        storage.getSlot(eventId, id)
      }
      val room = params.get("room").flatMap(_.headOption).flatMap{ room =>
        val id = UUIDFromString(URIBuilder(room).path.last.seg)
        storage.getRoom(eventId, id)
      }
      //TODO: improve this.
      var updated = session
      if (tags.isDefined) {
        updated = updated.withTags(tags.get.map(Tag).toSet[Tag])
      }
      if (slot.isDefined) {
        updated = updated.withSlot(slot.flatMap(_.id).get)
      }
      if (room.isDefined) {
        updated = updated.withRoom(room.flatMap(_.id).get)
      }
      if (updated == session) {
        NoContent
      } else {
        storage.saveSession(updated).fold(
          ex => InternalServerError ~> ResponseString(ex.getMessage),
          s => NoContent
        )
      }
    }
    val cj = for {
      _ <- PUT | GET
      res <- handleSession(eventId, sessionId)
    } yield {
      res
    }
    form | cj
  }

  private def handleSession(eventId: UUID, sessionId: UUID)(implicit u: User) = for {
    base <- baseURIBuilder
    a <- handleObject(storage.getSession(eventId, sessionId), (t: Template) => {
      toSession(eventId, Some(sessionId), t)
    }, storage.saveSession, (_: Session) => enrichedSessionToItem(base)(u)(storage.getSessionEnriched(eventId, sessionId).get), Some((_: Session) => storage.removeSession(sessionId))) {
      c =>
        val template = makeTemplate("title", "summary", "body", "outline", "audience", "equipment", "keywords", "published").
          addProperty(ValueProperty("lang").apply(ValueOptions, List(ValueOption("no"), ValueOption("en")))).
          addProperty(ValueProperty("format").apply(ValueOptions, Format.values.map(f => ValueOption(f.name)))).
          addProperty(ValueProperty("state").apply(ValueOptions, State.values.map(s => ValueOption(s.name)))).
          addProperty(ValueProperty("level").apply(ValueOptions, Level.values.map(l => ValueOption(l.name))))
        c.addQuery(Query(URIBuilder(c.href).segments("speakers").build(), "speaker by-email", List(
        ValueProperty("email")
      ), Some("Speaker by Email"))).withTemplate(template)
    }
  } yield a

  def handleSessionRoom(eventId: UUID, sessionId: UUID)(implicit u: User) = {
    for {
      _ <- GET
      a <- commit(getOrElse(storage.getSession(eventId, sessionId), NotFound))
      base <- baseURIBuilder
    } yield {
      HtmlContent ~> Html5(
        <html>
          <head><title>Rooms</title></head>
          <body>
            <form method="post" action={base.segments("events", eventId, "sessions", sessionId).toString}>
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
  }

  def handleChangelog(eventId: UUID)(implicit u: User) = for {
    _ <- GET
    _ <- commit
    _ <- authenticated(u)
    base <- baseURIBuilder
    href <- requestURI
    p <- queryParams
  } yield {
    val query = p("from").headOption.toRight("Missing from date").right.flatMap(s => RFC3339.parseDateTime(s))

    val items = query match {
      case Right(dt) => Right(storage.getChangedSessions(eventId, dt).map(converters.sessionToItem(base)))
      case Left(e) => Left(Error("Missing entity and " + e, None, None))
    }
    items.fold(
      msg => BadRequest ~> CollectionJsonResponse(JsonCollection(href, msg)),
      it => {
        CollectionJsonResponse(JsonCollection(href, Nil, it.toList)) ~> CacheControl("max-age=5,no-transform")
      })
  }


  private def getValidURIForPublish(eventId: UUID, u: URI): List[UUID] = {
    val path = u.getPath
    val index = path.indexOf(s"/events/$eventId/sessions/")
    if (index > 0) UUIDFromStringOpt(path.substring(path.lastIndexOf("/") + 1)).toList else Nil
  }

  private def publishNow(eventId: UUID, list: URIList) = {
    val sessions = list.list.flatMap(getValidURIForPublish(eventId, _))
    storage.publishSessions(eventId, sessions).fold(
      ex => BadRequest,
      _ => NoContent
    )
  }

  private def publish(eventId: UUID)(implicit user: User) = for {
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

  def handleSessionAttachments(eventId: UUID, sessionId: UUID)(implicit user: User) = {
    val get = for {
      _ <- GET
      href <- requestURI
      base <- baseURIBuilder
      obj <- getOrElse(storage.getSession(eventId, sessionId), NotFound)
    } yield {
      val atts = storage.getAttachments(sessionId)
      val items = atts.map(attachmentToItem(base))
      CollectionJsonResponse(JsonCollection(href, Nil, items.toList))
    }

    val cj = for {
      _ <- contentType(CollectionJsonResponse.contentType)
      either <- withTemplate(t => toAttachment(t))
      res <- either.right.flatMap(a => storage.saveAttachment(sessionId, a)) fold(
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
      storage.saveAttachment(sessionId, toURIAttachment(base.segments("binary"), att)).fold(
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
    if (attachment.id.isEmpty) {
      throw new IllegalStateException("Tried to convert an unsaved Attachment; Failure")
    }
    URIAttachment(None, base.segments(attachment.id.get).build(), attachment.name, attachment.size, attachment.mediaType)
  }
}
