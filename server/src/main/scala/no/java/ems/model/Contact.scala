package no.java.ems.model

import java.util.Locale
import no.java.ems.Attachment
import org.joda.time.DateTime

case class Contact(id: Option[String], name: String, bio: Option[String], emails: List[Email], locale: Locale = new Locale("no"), photo: Option[Attachment with Entity] = None, lastModified: DateTime = new DateTime()) extends Entity {

  type T = Contact

  def withId(id: String) = copy(id = Some(id))
}
