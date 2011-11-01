name := "ems-redux"

organization := "no.java"

version := "1.0-SNAPSHOT"

scalaVersion := "2.9.1"


libraryDependencies += "net.databinder" %% "unfiltered-filter" % "0.5.1"

libraryDependencies += "net.databinder" %% "unfiltered-jetty" % "0.5.1"

libraryDependencies += "joda-time" % "joda-time" % "2.0"

libraryDependencies += "org.joda" % "joda-convert" % "1.1" % "provided"

libraryDependencies += "net.hamnaberg.rest" % "json-collection" % "0.1-SNAPSHOT"


resolvers += "maven local" at "file:///Users/maedhros/.m2/repository"

publishArtifact in packageDoc := false 