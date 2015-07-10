package ems.model

import org.joda.time.{Duration, DateTime}

case class Slot(id: Option[String], eventId: String, start: DateTime, duration: Duration, parentId: Option[String] = None, lastModified: DateTime = DateTime.now()) extends Entity[Slot] {
  type T = Slot

  def withId(id: String) = copy(id = Some(id))
}

case class SlotTree(parent: Slot, children: Seq[Slot])
