import AppshKeys._

organization := "no.java.ems"

scalaVersion := "2.10.3"

scalacOptions := Seq("-deprecation", "-feature")

val unfilteredVersion = "0.8.0"

val joda = Seq(
  "joda-time" % "joda-time" % "2.2",
  "org.joda" % "joda-convert" % "1.2"
)

val unfiltered = Seq(
  "net.databinder" %% "unfiltered-filter" % unfilteredVersion,
  "net.databinder" %% "unfiltered-directives" % unfilteredVersion,
  "net.databinder" %% "unfiltered-jetty" % unfilteredVersion,
  "com.jteigen" %% "linx" % "0.1",
  "org.slf4j" % "slf4j-api" % "1.7.5"
)

libraryDependencies ++= joda ++ unfiltered ++ Seq(
  "org.ini4j" % "ini4j" % "0.5.2",
  "org.constretto" %% "constretto-scala" % "1.0",
  "org.constretto" % "constretto-core" % "2.1.4",
  "net.hamnaberg.rest" %% "scala-json-collection" % "2.2",
  "com.andersen-gott" %% "scravatar" % "1.0.2",
  "com.sksamuel.scrimage" %% "scrimage-core" % "1.3.12",
  "org.jsoup" % "jsoup" % "1.7.2",
  "commons-io" % "commons-io" % "2.3",
  "org.mongodb" %% "casbah-core" % "2.5.0",
  "org.mongodb" %% "casbah-gridfs" % "2.5.0",
  "org.mongodb" %% "casbah-query" % "2.5.0",
  "org.specs2" %% "specs2" % "1.12.3" % "test"
)


pomIncludeRepository := {
  x => false
}

crossPaths := false

aetherPublishBothSettings

appAssemblerSettings

appOutput in App := target.value / "appmgr" / "root"

appshSettings

appshBuild <<= appshBuild.dependsOn(appAssemble)
