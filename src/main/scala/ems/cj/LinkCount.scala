package ems.cj

import net.hamnaberg.json.collection.{Extensible, Link}
import net.hamnaberg.json.collection.extension.Extension
import org.json4s.JsonAST._


object LinkCount extends Extension[Link, Int] {
  def apply(like: Link): Int = {
    like.underlying \ "count" match {
      case JInt(v) => v.toInt
      case _ => -1
    }
  }

  def asJson(value: Int, parent: Extensible[_]): Seq[JField] = {
    if (value >= 0) Seq(JField("count", JInt(value))) else Nil
  }

}
