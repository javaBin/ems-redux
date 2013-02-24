package ems.model

object Slug {
  val Threshold = 32
  def makeSlug(input: String): String = {
    val target = input.trim.replaceAll("\\s", "_").replaceAll("[\\P{Alnum}&&[^_]]", "").toLowerCase()
    if (target.length > Threshold) target.substring(0, Threshold) else target
  }
}
