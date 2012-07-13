package ems

import Snippets._
import java.net.URI

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Pages {
  val login = template(
    <form action="/login" method="post">
      <input type="text" name="username"/>
      <input type="password" name="password"/>
    </form>
  )

  def events(href: URI) = template {
    <ul class="events"/>
  }
}
