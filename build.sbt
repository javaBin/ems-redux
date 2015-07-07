organization := "no.java.ems"

name := "ems"

scalaVersion := "2.11.4"

scalacOptions := Seq("-deprecation", "-feature")

val unfilteredVersion = "0.8.3"

val joda = Seq(
  "joda-time" % "joda-time" % "2.2",
  "org.joda" % "joda-convert" % "1.2"
)

val unfiltered = Seq(
  "net.databinder" %% "unfiltered-filter" % unfilteredVersion,
  "net.databinder" %% "unfiltered-directives" % unfilteredVersion,
  "net.databinder" %% "unfiltered-jetty" % unfilteredVersion,
  "com.jteigen" %% "linx" % "0.2",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "org.slf4j" % "slf4j-simple" % "1.7.7"
)

libraryDependencies ++= joda ++ unfiltered ++ Seq(
  "org.ini4j" % "ini4j" % "0.5.2",
  "org.constretto" %% "constretto-scala" % "1.1",
  "net.hamnaberg.rest" %% "scala-json-collection" % "2.3",
  "com.andersen-gott" %% "scravatar" % "1.0.3",
  "com.sksamuel.scrimage" %% "scrimage-core" % "1.4.2",
  "org.jsoup" % "jsoup" % "1.7.2",
  "commons-io" % "commons-io" % "2.3",
  "org.mongodb" %% "casbah-core" % "2.7.3",
  "org.mongodb" %% "casbah-gridfs" % "2.7.3",
  "org.mongodb" %% "casbah-query" % "2.7.3",
  "org.specs2" %% "specs2" % "2.4.2" % "test",
  "de.svenkubiak" % "jBCrypt" % "0.4"
)

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](
  scalaVersion,
  BuildInfoKey.action("version") { (version in ThisBuild ).value },
  BuildInfoKey.action("buildTime") { System.currentTimeMillis },
  BuildInfoKey.action("branch"){ Git.branch },
  BuildInfoKey.action("sha"){ Git.sha }
)

buildInfoPackage := "ems"
