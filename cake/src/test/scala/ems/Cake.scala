package ems

import java.io.File

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Cake extends App {
    unfiltered.jetty.Http(8080).resources(new File(getRoot, "src/main/webapp").toURL).plan(new Application).plan(EmsProxy).run(s => {
    println("The cake is a lie; Started")
  })

  def getRoot : File = {
    var parent = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getFile)
    while (!new File(parent, "src").exists()) {
      parent = parent.getAbsoluteFile.getParentFile
    }
    parent
  }
}
