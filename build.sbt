organization := "no.java.ems"

name := "ems"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.11.7")

scalacOptions := Seq("-deprecation", "-feature")

val unfilteredVersion = "0.8.4"

val joda = Seq(
  "joda-time" % "joda-time"     % "2.7",
  "org.joda"  % "joda-convert"  % "1.2"
)

val unfiltered = Seq(
  "net.databinder"  %% "unfiltered-filter"      % unfilteredVersion,
  "net.databinder"  %% "unfiltered-directives"  % unfilteredVersion,
  "net.databinder"  %% "unfiltered-jetty"       % unfilteredVersion,
  "com.jteigen"     %% "linx"                   % "0.2",
  "org.slf4j"       %  "slf4j-api"              % "1.7.7",
  "org.slf4j"       %  "slf4j-simple"           % "1.7.7"
)

val sql = Seq(
  "org.flywaydb"          %  "flyway-core"              % "3.2.1",
  "org.postgresql"        %  "postgresql"               % "9.4-1202-jdbc42",
  "com.typesafe.slick"    %% "slick"                    % "3.0.3",
  "com.github.tototoshi"  %% "slick-joda-mapper"        % "2.0.0"
)

libraryDependencies ++= joda ++ unfiltered ++ sql ++ Seq(
  "net.hamnaberg.rest"    %% "scala-json-collection"  % "2.3",
  "com.andersen-gott"     %% "scravatar"              % "1.0.3",
  "com.sksamuel.scrimage" %% "scrimage-core"          % "1.4.2",
  "org.jsoup"             %  "jsoup"                  % "1.8.2",
  "commons-io"            %  "commons-io"             % "2.3",
  "org.specs2"            %% "specs2-core"            % "3.6.2" % "test",
  "de.svenkubiak"         %  "jBCrypt"                % "0.4",
  "org.scalaz.stream"     %% "scalaz-stream"          % "0.7.2a",
  "io.argonaut"           %% "argonaut"               % "6.1",
  "no.arktekk"            %% "uri-template"           % "1.0.2"
)

enablePlugins(BuildInfoPlugin)

buildInfoPackage := "ems"

buildInfoKeys := Seq[BuildInfoKey](
  scalaVersion,
  BuildInfoKey.action("version") { (version in ThisBuild ).value },
  BuildInfoKey.action("buildTime") { System.currentTimeMillis },
  BuildInfoKey.action("branch"){ Git.branch },
  BuildInfoKey.action("sha"){ Git.sha }
)
