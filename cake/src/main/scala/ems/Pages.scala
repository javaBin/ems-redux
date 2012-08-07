package ems

import Snippets._
import java.net.URI

/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

object Pages {
  def login(cp: String) = template(cp,
    <form action="/login" method="post">
      <input type="text" name="username"/>
      <input type="password" name="password"/>
    </form>
  )
}
