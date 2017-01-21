package ems

import model._
import ems.converters._
import java.net.URI
import ems.util.URIBuilder
import security.User
import io.Source
import unfiltered.response._
import unfilteredx._
import unfiltered.request._
import net.hamnaberg.json.collection._
import ems.cj.{ValueOptions, ValueOption}
import java.io.OutputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SessionResources extends ResourceHelper {
  import Directives._
  import ops._

  def handleSlug(base: URIBuilder, eventId: UUID, slug: Option[String])(implicit user: User): ResponseDirective = {
    val href = base.segments("events", eventId, "sessions").build()
    (slug match {
      case Some(s) => {
        storage.getSessionBySlug(eventId, s).map(_.map(sessId => SeeOther ~> Location(URIBuilder(href).segments(sessId).toString)).getOrElse(NotFound))
      }
      case None => {
        storage.getSessionsEnriched(eventId).map{ sessions =>
          val items = sessions.map(enrichedSessionToItem(base))
          val template = makeTemplate("title", "summary", "body", "outline", "audience", "equipment", "tags", "keywords", "published").
            addProperty(ValueProperty("lang").apply(ValueOptions, List(ValueOption("no"), ValueOption("en")))).
            addProperty(ValueProperty("format").apply(ValueOptions, Format.values.map(f => ValueOption(f.name)))).
            addProperty(ValueProperty("level").apply(ValueOptions, Level.values.map(l => ValueOption(l.name))))
          val coll = JsonCollection(href, Nil, items.toList, List(
            Query(href, "session by-slug", List(ValueProperty("slug")), Some("By Slug"))
          )).withTemplate(template)
          CollectionJsonResponse(coll)
        }
      }
    }).successValue
  }


  def handleSessionList(eventId: UUID)(implicit u: User): ResponseDirective = {
    val get = for {
      _ <- GET
      params <- queryParams
      base <- baseURIBuilder
      list <- handleSlug(base, eventId, params("slug").headOption)
    } yield {
      list
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
    tags <- storage.getSessions(eventId).map(_.flatMap(_.abs.labels.getOrElse("tags", Nil)).toSet[String]).successValue
  } yield {
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
      params <- request.map{case Params(p) => p}
      session <- getOrElseF(storage.getSession(eventId, sessionId), NotFound)
      _ <- commit
      _ <- ifUnmodifiedSince(session.lastModified)
    } yield {
      val tags = params.get("tag").filterNot(_.isEmpty)
      //TODO: Replace with OptionT
      val slot = params.get("slot").flatMap(_.headOption).flatMap{ slot =>
        val id = UUIDFromString(URIBuilder(slot).path.last.seg)
        storage.getSlot(eventId, id).await()
      }
      //TODO: Replace with OptionT
      val room = params.get("room").flatMap(_.headOption).flatMap{ room =>
        val id = UUIDFromString(URIBuilder(room).path.last.seg)
        storage.getRoom(eventId, id).await()
      }

      val video = params.get("video").flatMap(_.headOption).map(URI.create).
        filter(uri => List("vimeo.com", "youtube.com").exists(uri.getHost.contains))
      //TODO: improve this.
      var updated = session
      if (video.isDefined) {
        updated = updated.copy(video = video)
      }
      if (tags.isDefined) {
        updated = updated.withTags(tags.get.toSet[String])
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
        storage.saveSession(updated).await()
        NoContent
      }
    }
    form | handleSession(eventId, sessionId)
  }

  implicit class AwaitingFuture[A](val f: scala.concurrent.Future[A]) {
    def await() = scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
  }

  private def handleSession(eventId: UUID, sessionId: UUID)(implicit u: User) = for {
    base <- baseURIBuilder
    enriched <- storage.getSessionEnriched(eventId, sessionId).successValue
    previous <- commit(getOrElse(enriched, NotFound))
    a <- handleObject(Future.successful(enriched.map(_.session)), (t: Template) => {
      val updated = toSession(eventId, Some(sessionId), t)
      updated.copy(room = previous.session.room, slot = previous.session.slot, published = false, video = previous.session.video)
    }, storage.saveSession, (_: Session) => enriched.map(s => enrichedSessionToItem(base)(u)(s)).get, Some((_: Session) => storage.removeSession(sessionId))) {
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
      a <- commit(getOrElseF(storage.getSession(eventId, sessionId), NotFound))
      base <- baseURIBuilder
    } yield {
      HtmlContent ~> Html5(
        <html>
          <head><title>Rooms</title></head>
          <body>
            <form method="post" action={base.segments("events", eventId, "sessions", sessionId).toString}>
              <select name="room" id="room">
                {
                  storage.getRooms(eventId).map(_.map{r =>
                     <option value={base.segments("events", eventId, "rooms", r.id.get).toString}>{r.name}</option>
                  }).await()
                }
              </select>
            </form>
          </body>
        </html>
      )
    }
  }

  /*def handleChangelog(eventId: UUID)(implicit u: User) = for {
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
  }*/


  private def getValidURIForPublish(eventId: UUID, u: URI): List[UUID] = {
    val path = u.getPath
    val index = path.indexOf(s"/events/$eventId/sessions/")
    if (index > 0) UUIDFromStringOpt(path.substring(path.lastIndexOf("/") + 1)).toList else Nil
  }

  private def publishNow(eventId: UUID, list: URIList) = {
    val sessions = list.list.flatMap(getValidURIForPublish(eventId, _))
    storage.publishSessions(eventId, sessions).map(_ => NoContent).successValue
  }

  private def publish(eventId: UUID)(implicit user: User): ResponseDirective = for {
    _ <- POST
    _ <- authenticated(user)
    _ <- contentType("text/uri-list")
    is <- inputStream
    res <- URIList.parse(Source.fromInputStream(is)).fold(
      ex => failure(BadRequest),
      list => success(list)
    )
    published <- publishNow(eventId, res)
  } yield {
    published
  }
}
