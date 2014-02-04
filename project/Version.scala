import sbt._

object Version {
  def gitSha = Process("git rev-parse --short HEAD").lines.head

  def apply(base: String) = {
    if (base.endsWith("SNAPSHOT")) base else base + "-" + gitSha
  }
}