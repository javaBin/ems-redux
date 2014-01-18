import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Path, Files}
import sbt._
import sbt.Keys._
import com.earldouglas.xsbtwebplugin.WebappPlugin._
import com.typesafe.sbt.SbtStartScript._

object Build extends sbt.Build {

  val liftJSONversion = "2.4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.java",
    scalaVersion := "2.10.1",
    scalacOptions := Seq("-deprecation"),
    pomIncludeRepository := {
      x => false
    },
    resolvers += Resolvers.sonatypeNexusSnapshots,
    crossPaths := false
  )

  //TODO: Sbt should produce a POM artifact with modules for the aggregates
  lazy val root = Project(
    id = "ems",
    base = file("."),
    settings = buildSettings ++ Seq(
      name := "ems"
    )
  ) aggregate(config, server, cake, jetty)

  lazy val config = module("config")(settings = Seq(
    libraryDependencies ++= Dependencies.config
  ) ++ webappSettings)

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
    libraryDependencies ++= Dependencies.dist
  ))

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
      "org.eclipse.jetty.aggregate" % "jetty-all" % "9.1.0.v20131115"
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
      "org.slf4j" % "slf4j-simple" % "1.7.5",
      "javax.servlet" % "servlet-api" % "2.5" % "provided"
    )
  }

  object Dist {
    import com.earldouglas.xsbtwebplugin._

    val serverDist = TaskKey[File]("dist", "Creates a distributable zip file containing the publet standalone server.")

    lazy val defaultDist = serverDist <<= (baseDirectory in Compile, packageBin in Compile, managedClasspath in Compile, PluginKeys.packageWar.in(server).in(Compile), PluginKeys.packageWar.in(cake).in(Compile), Keys.target, Keys.name, Keys.version) map { (base: File, runner: File, cp: Keys.Classpath, server:File, cake: File, target:File, name:String, version:String) =>
      val distdir = target / (name +"-"+ version)
      val zipFile = target / (name +"-"+ version +".zip")
      IO.delete(zipFile)
      IO.delete(distdir)

      val distSrc = base / "src" / "dist"
      val root = distdir / "root"
      val runnerJar = root / "jetty.jar"
      val lib = root / "lib"
      val webapps = root / "webapps"

      IO.createDirectories(Seq(distdir, root, lib, webapps))

      IO.copyDirectory(distSrc, distdir, overwrite = false)

      val bin = root / "bin"

      def fixPermissions(path: Path) = {
        val permissions = new java.util.HashSet(Files.getPosixFilePermissions(path))
        permissions.add(PosixFilePermission.OWNER_EXECUTE)
        permissions.add(PosixFilePermission.GROUP_EXECUTE)
        permissions.add(PosixFilePermission.OTHERS_EXECUTE)
        Files.setPosixFilePermissions(path, permissions)
      }

      IO.listFiles(bin).foreach(f => fixPermissions(f.toPath))

      IO.copyFile(runner, runnerJar)

      cp.foreach(f => IO.copyFile(f.data, lib / f.data.getName))

      val runnerFile = root / "jetty.jar"
      IO.copyFile(runner, runnerFile)

      val serverFile = webapps / "server.war"
      IO.copyFile(server, serverFile)

      val cakeFile = webapps / "admin.war"
      IO.copyFile(cake, cakeFile)

      def entries(f: File):List[File] = f :: (if (f.isDirectory) IO.listFiles(f).toList.flatMap(entries) else Nil)

      IO.zip(entries(distdir).map(d => (d, d.getAbsolutePath.substring(distdir.getParent.length))), zipFile)
      zipFile
    }
  }
}

