
pomIncludeRepository := {
  x => false
}

crossPaths := false

publishTo <<= (version) apply {
  (v: String) => if (v.trim().endsWith("SNAPSHOT")) {
    Some("JavaBin Nexus repo" at "http://nye.java.no/nexus/content/repositories/snapshots")
  }
  else {
    Some("JavaBin Nexus repo" at "http://nye.java.no/nexus/content/repositories/releases")
  }
}

credentials += Credentials(Path.userHome / ".sbt" / "javabin.credentials")

aetherPublishBothSettings

appAssemblerSettings

appOutput in App := target.value / "appmgr" / "root"

appmgrSettings

appmgrBuild <<= appmgrBuild.dependsOn(appAssemble)

appmgrLauncher in Appmgr := (appmgrLauncher in Appmgr).value.map(_.copy(command = "jetty", name = "ems"))

aetherArtifact <<= (aetherArtifact, appmgrBuild) map { (art, build) =>
  art.attach(build, "appmgr", "zip")
}
