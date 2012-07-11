package no.java.ems.model

import org.joda.time.DateTime

/**
 * Created by IntelliJ IDEA.
 * User: maedhros
 * Date: 10/26/11
 * Time: 11:12 PM
 * To change this template use File | Settings | File Templates.
 */

trait Entity {
  type T <: Entity

  def id: Option[String]

  def lastModified: DateTime

  def withId(id: String): T
}
