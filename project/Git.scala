
import sbt._
import scala.util.Try

object Git {
  def branch = Try {
    // Git exports an environment that tracks which branch is current (e.g. master or dev or ...)
    val env = Option(System.getenv("GIT_BRANCH")).map{ _.split("/").last }

    // If no environment variable, do it manually
    // git symbolic-ref HEAD prints out the current branch, e.g. refs/heads/master
    // --short option removes refs/heads so we get master
    // -q option supress error messages
    // !! is an sbt thing that executes the command and returns a String
    env.getOrElse("git symbolic-ref --short -q HEAD".!!.trim())
  }.getOrElse("No git branch")


  def sha = Try { "git rev-parse --short HEAD".!!.trim() }.getOrElse("")
}
