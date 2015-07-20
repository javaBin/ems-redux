package ems
package model

import ems.util.RFC3339
import org.joda.time.{Duration, DateTime}

case class Slot(id: Option[UUID], eventId: UUID, start: DateTime, duration: Duration, parentId: Option[UUID] = None, lastModified: DateTime = DateTime.now()) extends Entity[Slot] {
  type T = Slot

  def withId(id: UUID) = copy(id = Some(id))

  def formatted = RFC3339.format(start) + "+" + RFC3339.format(start.plus(duration))
}

case class SlotTree(parent: Slot, children: Seq[Slot])
