package ems
package model

case class Email(address: String)

case class Tag(name: String) {
  require(!name.contains(","), "Tag must NOT contain any commas")
}

case class Keyword(name: String) {
  require(!name.contains(","), "Keyword must NOT contain any commas")
}
