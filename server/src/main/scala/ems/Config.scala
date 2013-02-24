package no.java.ems

import java.io.File
import org.constretto.ConstrettoBuilder
import org.constretto.model.Resource

case class Config(binaryStorage: File, mongoUrl: String)

object Config {
  lazy val default = {
    val c = new ConstrettoBuilder().
      createIniFileConfigurationStore().
      addResource(Resource.create("classpath:config.ini")).
      addResource(Resource.create("file:/opt/jb/ems-redux/config.ini")).
      done().
      createSystemPropertiesStore().getConfiguration
    Config(new File(c.evaluateToString("binary-storage")), c.evaluateToString("mongo-url"))
  }
}
