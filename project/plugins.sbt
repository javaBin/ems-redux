// IDEA plugin
resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.4")

addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.6.1")
