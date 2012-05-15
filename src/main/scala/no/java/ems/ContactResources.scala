package no.java.ems

import unfiltered.response._
import no.java.unfiltered.{RequestContentDisposition, RequestURIBuilder, BaseURIBuilder}
import javax.servlet.http.HttpServletRequest
import unfiltered.request._
import net.hamnaberg.json.collection.{Template, JsonCollection, ErrorMessage}
import converters._


/**
 * @author Erlend Hamnaberg<erlend.hamnaberg@arktekk.no>
 */

trait ContactResources extends ResourceHelper { this: Storage =>

  def handleContactList(request: HttpRequest[HttpServletRequest]) = {
    request match {
      case GET(_) & BaseURIBuilder(baseUriBuilder) => {
        val output = this.getContacts().map(contactToItem(baseUriBuilder))
        val href = baseUriBuilder.segments("contacts").build()
        CollectionJsonResponse(JsonCollection(href, Nil, output))
      }
      case req@POST(RequestContentType(CollectionJsonResponse.contentType)) => {
        withTemplate(req) {
          t => {
            val c = toContact(None, t)
            this.saveContact(c)
            NoContent
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case _ => MethodNotAllowed
    }
  }

  def handleContact(id: String, request: HttpRequest[HttpServletRequest]) = {
    val contact = this.getContact(id)
    val base = BaseURIBuilder.unapply(request).get
    handleObject(contact, request, (t: Template) => toContact(Some(id), t), contactToItem(base))
  }

  def handleContactPhoto(id: String, request: HttpRequest[HttpServletRequest]) = {
    request match {
      case POST(_) & RequestContentType(ct) if (MIMEType.ImageAll.includes(MIMEType(ct).get)) => {
        request match {
          case RequestContentDisposition(cd) => {
            val contact = this.getContact(id)
            if (contact.isDefined) {
              val binary = this.saveAttachment(StreamingAttachment(cd.filename.getOrElse(cd.filenameSTAR.get.filename), None, MIMEType(ct), request.inputStream))
              this.saveContact(contact.get.copy(image = Some(binary)))
              NoContent
            }
            else {
              NotFound
            }
          }
          case _ => {
            val builder = RequestURIBuilder.unapply(request).get
            BadRequest ~> CollectionJsonResponse(
              JsonCollection(
                builder.build(),
                ErrorMessage("Missing Content Disposition", None, Some("You need to add a Content-Disposition header."))
              )
            )
          }
        }
      }
      case POST(_) => UnsupportedMediaType
      case GET(_) & BaseURIBuilder(b) => {
        val contact = this.getContact(id)
        val image = contact.flatMap(_.photo.map(i => b.segments("binary", i.id.get).build()))
        if (image.isDefined) Redirect(image.get.toString) else MethodNotAllowed
      }
      case _ => MethodNotAllowed
    }
  }


}
