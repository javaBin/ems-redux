package ems

import java.net.URI
import org.constretto.ConstrettoBuilder
import org.constretto.model.Resource

case class Config(server: URI, password: String)

object Config {
  lazy val default = {
    val c = new ConstrettoBuilder().
      createIniFileConfigurationStore().
      addResource(Resource.create("classpath:/config.ini")).
      addResource(Resource.create("file:/opt/jb/ems-redux/config.ini")).
      done().
      createSystemPropertiesStore().getConfiguration
    Config(URI.create(c.evaluateToString("server")), c.evaluateToString("password"))
  }
}
