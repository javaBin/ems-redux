package no.java.ems

import java.net.URI
import org.joda.time.format.ISODateTimeFormat
import net.hamnaberg.json._
import scala.collection.JavaConversions._

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 11/8/11
 * Time: 10:11 PM
 * To change this template use File | Settings | File Templates.
 */

object converters {
  
  def eventToItem(implicit base: URI): (Event) => Item = {
    e => {
      val dateFormat = ISODateTimeFormat.basicDateTimeNoMillis()
      val properties = Map(
        "name" -> e.title,
        "start" -> dateFormat.print(e.start),
        "end" -> dateFormat.print(e.end)
      ).map(toProperty).toList
      new Item(base.resolve("/events/" + e.id.get), properties, new Link(base.resolve("/events/%s/sessions".format(e.id.get)), "sessions", "Sessions") :: Nil)
    }
  }

  def sessionToItem(implicit base: URI): (Session) => Item = {
    s => {
      val properties = Map(
        "title" -> s.sessionAbstract.title,
        "body" -> s.sessionAbstract.body.getOrElse(""),
        "lead" -> s.sessionAbstract.lead.getOrElse(""),
        "format" -> s.sessionAbstract.format.toString,
        "level" -> s.sessionAbstract.level.toString,
        "state" -> s.state.toString,
        "tags" -> s.tags.map(_.name).mkString(","),
        "keywords" -> s.keywords.map(_.name).mkString(",")
      ).map(toProperty).toList
      new Item(base.resolve("/events/%s/sessions/%s".format(s.eventId, s.id.get)), properties, Nil)
    }
  }

  def toProperty: PartialFunction[(String, AnyRef), Property] = {
    case (a, b) => new Property(a, ValueFactory.createValue(b), a)
  }

  def singleCollection: (Item) => JsonCollection = {
    item => new DefaultJsonCollection(item.getHref, item.getLinks, List(item), Nil, null)
  }

  def errorCollection(href: URI, message: ErrorMessage): JsonCollection = {
    new ErrorJsonCollection(href, message)
  }

  def list2Collection(uri: URI, items: List[Item], links: List[Link] = Nil, template: Option[Template] = None): JsonCollection = {
    new DefaultJsonCollection(
      uri,
      links,
      items,
      Nil,
      template.getOrElse(null)
    )
  }
}