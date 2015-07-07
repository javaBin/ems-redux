
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

credentials ++= {
  val cred = Path.userHome / ".sbt" / "javabin.credentials"
  if (cred.exists) Seq(Credentials(cred)) else Nil
}

overridePublishBothSettings

target in App := target.value / "appmgr" / "root"

packageBin in Appmgr <<= (packageBin in Appmgr).dependsOn(packageBin in App)

appmgrLauncher in Appmgr := (appmgrLauncher in Appmgr).value.map(_.copy(command = "jetty", name = "ems"))

aether.AetherKeys.aetherArtifact <<= (aether.AetherKeys.aetherArtifact, (packageBin in Appmgr)) map { (art, build) =>
  art.attach(build, "appmgr", "zip")
}
