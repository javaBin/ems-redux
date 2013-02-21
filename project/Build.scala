import sbt._
import sbt.Keys._
import com.github.siasia.WebappPlugin._

object Build extends sbt.Build {

  val liftJSONversion = "2.4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.java",
    scalaVersion := "2.9.1",
    scalacOptions := Seq("-deprecation"),
    pomIncludeRepository := {
      x => false
    },
    crossPaths := false,
    credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
  )

  //TODO: Sbt should produce a POM artifact with modules for the aggregates
  lazy val root = Project(
    id = "ems",
    base = file("."),
    settings = buildSettings ++ Seq(
      name := "ems"
    )
  ) aggregate(wiki, server, cake, jetty)

  lazy val wiki = module("wiki")(settings = Seq(
    libraryDependencies := Dependencies.wiki,
    javacOptions += Seq("-source", "1.6", "-target", "1.6")
  ))

  lazy val server = module("server")(settings = Seq(
    libraryDependencies ++= Dependencies.server
  ) ++ webappSettings).dependsOn(wiki)

  lazy val cake = module("cake")(settings = Seq(
    description := "The cake is a lie",
    libraryDependencies := Dependencies.cake
  ) ++ webappSettings)

  lazy val jetty = module("jetty")(settings = Seq(
    libraryDependencies ++= Dependencies.jetty
  )).dependsOn(cake, server)

  private def module(moduleName: String)(
    settings: Seq[Setting[_]],
    projectId: String = "ems-" + moduleName,
    dirName: String = moduleName,
    srcPath: String = "ems/" + moduleName.replace("-","/")
    ) = Project(projectId, file(dirName),
    settings = (buildSettings :+
      srcPathSetting(projectId, srcPath)) ++ settings)

  def srcPathSetting(projectId: String, rootPkg: String) = {
    mappings in (LocalProject(projectId), Compile, packageSrc) ~= {
      defaults: Seq[(File,String)] =>
        defaults.map { case(file, path) =>
          (file, rootPkg + "/" + path)
        }
    }
  }


  object Resolvers {
    val sonatypeNexusSnapshots = "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    val sonatypeNexusStaging = "Sonatype Nexus Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  }

  object Dependencies {

    lazy val server = joda ++ testDeps ++ unfiltered ++ Seq(
      "net.hamnaberg.rest" %% "scala-json-collection" % "1.1.1",
      "commons-io" % "commons-io" % "2.3",
      "org.mongodb" %% "casbah-core" % "2.5.0",
      "org.mongodb" %% "casbah-gridfs" % "2.5.0",
      "org.mongodb" %% "casbah-query" % "2.5.0"
    )

    lazy val cake = unfiltered ++ testDeps ++ joda ++ Seq(
      "net.databinder.dispatch" %% "dispatch-core" % "0.9.5"
    )

    lazy val jetty = Seq("net.databinder" %% "unfiltered-jetty" % "0.6.3")

    lazy val wiki = Seq("commons-io" % "commons-io" % "2.3", "junit" % "junit" % "4.11" % "test")

    private lazy val testDeps = Seq(
      "org.specs2" %% "specs2" % "1.12.3" % "test"
    )

    private lazy val joda = Seq(
      "joda-time" % "joda-time" % "2.1",
      "org.joda" % "joda-convert" % "1.1"
    )

    private lazy val unfiltered = Seq(
      "net.databinder" %% "unfiltered-filter" % "0.6.3",
      "javax.servlet" % "servlet-api" % "2.5" % "provided",
      "net.databinder" %% "unfiltered-jetty" % "0.6.3" % "test"
    )
  }
}
