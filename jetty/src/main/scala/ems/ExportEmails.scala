package ems

import dispatch._
import net.hamnaberg.json.collection.{JsonCollection, NativeJsonCollectionParser}
import java.io.StringReader
import com.ning.http.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global

object ExportEmails extends App {
  if (args.length >= 3) {
    val username = args(1)
    val password = args(2)
    val sessions = for {
      cj <- loadJson(args(0), username, password)
      items <- Future {cj.items.map{ i =>
        (i.getPropertyValue("title").map(_.value.toString).get, i.findLinkByRel("speaker collection").map(_.href.toString).get)
      }}
    } yield {
      items
    }

    val toSpeakerRefs = (cj: JsonCollection) => {
      cj.items.flatMap(i =>
        i.getPropertyValue("name").map(_.value.toString) -> i.getPropertyValue("email").map(_.value.toString) match {
          case (Some(n), Some(e)) => Some(SpeakerRef(n, e))
          case _ => None
        }
      )
    }

    val foo = for {
      list <- sessions
      ses <- Future.sequence(list.map{ case (title, href) => loadJson(href, username, password).map(cj => SessionWithSpeakers(title, toSpeakerRefs(cj)))})
    } yield {
      ses
    }

    println(foo())

  }

  def loadJson(uri: String, username: String, password: String) = {
    for {
      res <- Http(url(uri) <:< Seq("Authorization" -> ("Basic " + Base64.encode(s"$username:$password".getBytes("UTF-8")).trim)))
      coll <- Future{ NativeJsonCollectionParser.parseCollection(new StringReader(res.getResponseBody))}
    } yield {
      coll.fold(fa => throw fa, identity)
    }
  }
  case class SpeakerRef(name: String, email: String)

  case class SessionWithSpeakers(title: String, speakers: Seq[SpeakerRef] = Nil)
}
