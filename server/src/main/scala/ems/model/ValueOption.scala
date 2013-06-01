package ems.model

import net.hamnaberg.json.collection.extension.Extension
import net.hamnaberg.json.collection.{Property, Extensible}
import org.json4s._

case class ValueOption(option: String) {
  def toJson = JString(option)
}

object ValueOptions extends Extension[Property, Seq[ValueOption]] {
  def apply(like: Property): Seq[ValueOption] = {
    like.underlying.findField{case (n,_) => n == "options"}.map{
      case (_, JArray(lst)) => lst.map(v => ValueOption(v.values.toString))
      case _ => Nil
    }.getOrElse(Nil)
  }

  def asJson(ext: Seq[ValueOption], parent: Extensible[_]): Seq[JField] = Seq("options" -> JArray(ext.map(_.toJson).toList))

}