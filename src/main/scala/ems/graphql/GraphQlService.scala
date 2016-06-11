package ems.graphql

import java.util.UUID

import ems.model.{Event, Session, Speaker}
import ems.security.Anonymous
import ems.storage.DBStorage

import scala.concurrent.{ExecutionContext, Future}

class GraphQlService(store: DBStorage)(implicit executionContext: ExecutionContext) {

  def getSpeakers(sessionId: UUID): Future[List[Speaker]] = {
    store.getSpeakers(sessionId).map(_.toList)
  }

  def getSessions(eventId: UUID): Future[List[Session]] = {
    store.getSessions(eventId)(Anonymous).map(_.toList)
  }

  def getEvents(ids: Option[Seq[String]]): Future[List[Event]] = {
    ids.getOrElse(List()) match {
      case Nil => store.getEvents().map(_.toList)
      case idsAsString => {
        val queryIds: Seq[UUID] = idsAsString.map(UUID.fromString)
        Future.sequence(queryIds.map(store.getEvent).toList)
            .map(optEvent => optEvent.flatMap(_.toList))
      }
    }
  }
}
