
import java.io.FileWriter
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import sbt._
import sbt.Keys._
import com.earldouglas.xsbtwebplugin.WebappPlugin._
import com.typesafe.sbt.SbtStartScript._
import aether.Aether._

object Build extends sbt.Build {

  val liftJSONversion = "2.4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.java.ems",
    scalaVersion := "2.10.3",
    scalacOptions := Seq("-deprecation"),
    pomIncludeRepository := {
      x => false
    },
    crossPaths := false
  ) ++ aetherPublishBothSettings

  //TODO: Sbt should produce a POM artifact with modules for the aggregates
  lazy val root = Project(
    id = "ems",
    base = file("."),
    settings = buildSettings ++ Seq(
      name := "ems"
    )
  ) aggregate(config, server, cake, jetty)

  lazy val buildInfo = resourceGenerators in Compile <+=
    (resourceManaged in Compile, version) map { (dir, v) =>
      val file = dir / "build-info.properties"
      val contents = "version=%s\ngit-sha=%s\n".format(v, Version.gitSha)
      IO.write(file, contents)
      Seq(file)
    }


  lazy val config = module("config")(settings = Seq(
    libraryDependencies ++= Dependencies.config,
    buildInfo
  ))

  lazy val server = module("server")(settings = Seq(
    libraryDependencies ++= Dependencies.server
  ) ++ webappSettings).dependsOn(config)

  lazy val cake = module("cake")(settings = Seq(
    description := "The cake is a lie",
    libraryDependencies := Dependencies.cake
  ) ++ webappSettings).dependsOn(config)

  lazy val jetty = module("jetty")(settings = Seq(
    libraryDependencies ++= Dependencies.jetty
  ) ++ startScriptForClassesSettings).dependsOn(cake, server)

  lazy val dist = module("dist")(settings = Seq(
    Dist.defaultDist,
    libraryDependencies ++= Dependencies.dist,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
    ) ++ addArtifact(Artifact("ems-dist", "zip", "zip", "appsh"), Dist.serverDist in Compile)
  )

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
    val unfilteredVersion = "0.6.8"

    lazy val server = joda ++ testDeps ++ unfiltered ++ Seq(
      "net.hamnaberg.rest" %% "scala-json-collection" % "2.2",
      "com.andersen-gott" %% "scravatar" % "1.0.2",
      "com.sksamuel.scrimage" %% "scrimage-core" % "1.3.12",
      "org.jsoup" % "jsoup" % "1.7.2",
      "commons-io" % "commons-io" % "2.3",
      "org.mongodb" %% "casbah-core" % "2.5.0",
      "org.mongodb" %% "casbah-gridfs" % "2.5.0",
      "org.mongodb" %% "casbah-query" % "2.5.0"
    )

    lazy val cake = unfiltered ++ testDeps ++ joda ++ Seq(
      "net.databinder.dispatch" %% "dispatch-core" % "0.10.1",
      "commons-codec" % "commons-codec" % "1.7"
    )

    lazy val jetty = Seq("net.databinder" %% "unfiltered-jetty" % unfilteredVersion)
    lazy val dist = Seq(
      "org.eclipse.jetty.aggregate" % "jetty-all" % "9.1.0.v20131115",
      "org.slf4j" % "slf4j-simple" % "1.7.5"
    )

    val config = Seq(
      "org.constretto" % "constretto-core" % "2.0.3",
      "org.constretto" %% "constretto-scala" % "1.0",
      "org.ini4j" % "ini4j" % "0.5.2"
    )

    private val testDeps = Seq(
      "org.specs2" %% "specs2" % "1.12.3" % "test"
    )

    private val joda = Seq(
      "joda-time" % "joda-time" % "2.1",
      "org.joda" % "joda-convert" % "1.2"
    )

    private val unfiltered = Seq(
      "net.databinder" %% "unfiltered-filter" % unfilteredVersion,
      "net.databinder" %% "unfiltered-directives" % unfilteredVersion,
      "org.slf4j" % "slf4j-api" % "1.7.5",
      "javax.servlet" % "servlet-api" % "2.5" % "provided"
    )
  }

  object Dist {
    import com.earldouglas.xsbtwebplugin._

    val serverDist = TaskKey[File]("dist", "Creates a app.sh zip file.")

    lazy val defaultDist = serverDist <<= (baseDirectory in Compile, packageBin in Compile, managedClasspath in Compile, sourceDirectory.in(config).in(Compile), PluginKeys.packageWar.in(server).in(Compile), PluginKeys.packageWar.in(cake).in(Compile), Keys.target, Keys.name, Keys.version) map { (base: File, runner: File, cp: Keys.Classpath, configSrc:File, server:File, cake: File, target:File, name:String, version:String) =>
      val distdir = target / (name +"-"+ version)
      val zipFile = target / (name +"-"+ version +".zip")
      IO.delete(zipFile)
      IO.delete(distdir)

      val distSrc = base / "src" / "dist"
      val root = distdir / "root"
      val runnerJar = root / "jetty.jar"
      val lib = root / "lib"
      val etc = root / "etc"
      val webapps = root / "webapps"

      IO.createDirectories(Seq(distdir, root, lib, webapps))

      IO.copyDirectory(distSrc, distdir, overwrite = false)

      cp.foreach(f => IO.copyFile(f.data, lib / f.data.getName))

      IO.copyFile(runner, runnerJar)

      IO.copyFile(configSrc / "resources" / "config.ini", etc / "config.ini")

      IO.copyFile(server, webapps / "server.war")

      IO.copyFile(cake, webapps / "admin.war")

      def entries(f: File):List[File] = f :: (if (f.isDirectory) IO.listFiles(f).toList.flatMap(entries) else Nil)

      def zip(files: Seq[File], file: File) {
        val entries = files.map{ f =>
          val path = f.getAbsolutePath.substring(distdir.toString.length)
          val e = new ZipArchiveEntry(f, if (path.startsWith("/")) path.substring(1) else path)
          if (path.contains("bin") || path.contains("hooks") && f.isFile) {
            e.setUnixMode(0755)
          }
          (f, e)
        }

        val os = new ZipArchiveOutputStream(file)
        entries.foreach{case (f, e) =>
          os.putArchiveEntry(e)
          if (!f.isDirectory) {
            IO.transfer(f, os)
          }
          os.closeArchiveEntry()
        }
        os.close()
      }
      
      zip(entries(distdir).tail, zipFile)
      println("Wrote " + zipFile)
      zipFile
    }
  }
}

