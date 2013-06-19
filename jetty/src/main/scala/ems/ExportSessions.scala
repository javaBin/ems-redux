package ems

import dispatch._
import net.hamnaberg.json.collection.{Item, JsonCollection, NativeJsonCollectionParser}
import java.io.StringReader
import com.ning.http.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import ems.model.{Format, State, Tag}
import java.net.URI

object ExportSessions extends App {
  if (args.length >= 3) {
    val username = args(1)
    val password = args(2)
    val sessions = for {
      cj <- loadJson(args(0), username, password)
      sessions <- Future {cj.items.map{ i =>
        SessionRef(i.href, getPropertyValueAsString(i, "title"), Format(getPropertyValueAsString(i, "format")), State(getPropertyValueAsString(i, "state")), i.getPropertyAsSeq("tags").map(v => v.value.toString).toSet[String])
      }}
    } yield sessions

    val filteredSessions = sessions().filter(s => s.format == Format.Presentation && s.tags.contains("ja"))
    println(s"Found ${filteredSessions.size} after filtering")
    filteredSessions.foreach(s =>
      println(s"${s.title}|${s.format}|${s.tags.mkString(",")}")
    )
  }


  def getPropertyValueAsString(i: Item, name: String): String = {
    i.getPropertyValue(name).map(_.value.toString).get
  }

  def loadJson(uri: String, username: String, password: String) = {
    for {
      res <- Http(url(uri) <:< Seq("Authorization" -> ("Basic " + Base64.encode(s"$username:$password".getBytes("UTF-8")).trim)))
      coll <- Future{ NativeJsonCollectionParser.parseCollection(new StringReader(res.getResponseBody))}
    } yield {
      coll.fold(fa => throw fa, identity)
    }
  }

  case class SessionRef(href: URI, title: String, format: Format, state: State, tags: Set[String])
}
