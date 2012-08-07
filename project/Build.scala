import sbt._
import sbt.Keys._
import xml.Group
import aether._
import com.github.siasia.WebappPlugin._

object Build extends sbt.Build {

  val liftJSONversion = "2.4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.java",
    scalaVersion := "2.9.1",
    scalacOptions := Seq("-deprecation"),
    publishTo <<= (version) apply {
      (v: String) => if (v.trim().endsWith("SNAPSHOT")) Some(Resolvers.sonatypeNexusSnapshots) else Some(Resolvers.sonatypeNexusStaging)
    },
    pomIncludeRepository := {
      x => false
    },
    credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
  ) ++ Aether.aetherPublishSettings

  //TODO: Sbt should produce a POM artifact with modules for the aggregates
  lazy val root = Project(
    id = "ems",
    base = file("."),
    settings = buildSettings ++ Seq(
      name := "ems"
    ) ++ mavenCentralFrouFrou
  ) aggregate(server, cake, jetty)

  lazy val server = module("server")(settings = Seq(
    libraryDependencies := Dependencies.server,
    manifestSetting
  ) ++ webappSettings)

  lazy val cake = module("cake")(settings = Seq(
    description := "The cake is a lie",
    libraryDependencies := Dependencies.cake,
    manifestSetting
  ) ++ webappSettings)

  lazy val jetty = module("jetty")(settings = Seq(
    libraryDependencies := Dependencies.jetty
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

  lazy val manifestSetting = packageOptions <+= (name, version, organization) map {
    (title, version, vendor) =>
      Package.ManifestAttributes(
        "Created-By" -> "Simple Build Tool",
        "Built-By" -> System.getProperty("user.name"),
        "Build-Jdk" -> System.getProperty("java.version"),
        "Specification-Title" -> title,
        "Specification-Version" -> version,
        "Specification-Vendor" -> vendor,
        "Implementation-Title" -> title,
        "Implementation-Version" -> version,
        "Implementation-Vendor-Id" -> vendor,
        "Implementation-Vendor" -> vendor
      )
  }

  // Things we care about primarily because Maven Central demands them
  lazy val mavenCentralFrouFrou = Seq(
    homepage := Some(new URL("http://github.com/javaBin/ems-redux")),
    startYear := Some(2011),
    licenses := Seq(("Apache 2", new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))),
    pomExtra <<= (pomExtra) {
      (pom) => pom ++ Group(
        <scm>
          <url>http://github.com/javaBin/ems-redux</url>
          <connection>scm:git:git://github.com/javaBin/ems-redux.git</connection>
          <developerConnection>scm:git:git@github.com:javaBin/ems-redux.git</developerConnection>
        </scm>
          <developers>
            <developer>
              <id>hamnis</id>
              <name>Erlend Hamnaberg</name>
              <url>http://twitter.com/hamnis</url>
            </developer>
          </developers>
      )
    }
  )

  object Dependencies {

    lazy val server = joda ++ testDeps ++ unfiltered ++ Seq(
      "net.hamnaberg.rest" %% "scala-json-collection" % "1.1",
      "commons-io" % "commons-io" % "2.3",
      "org.mongodb" %% "casbah-core" % "2.4.0",
      "org.mongodb" %% "casbah-gridfs" % "2.4.0",
      "org.mongodb" %% "casbah-query" % "2.4.0"
    )

    lazy val cake = unfiltered ++ testDeps ++ joda ++ Seq(
      "net.databinder" %% "dispatch-http" % "0.8.8"
    )

    lazy val jetty = Seq("net.databinder" %% "unfiltered-jetty" % "0.6.3")

    private lazy val testDeps = Seq(
      "org.specs2" %% "specs2" % "1.11" % "test"
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
