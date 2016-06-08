package ems.graphql

import java.util.UUID

import ems.util.RFC3339
import org.joda.time.DateTime
import sangria.marshalling.MarshallerCapability
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation
import sangria.ast

import scala.util.{Failure, Success, Try}

object DataTypes {

  case object UUIDCoercionViolation extends ValueCoercionViolation("UUID value expected")

  case object DateTimeCoercionViolation extends ValueCoercionViolation("DateTime value expected")

  implicit val UUIDType = ScalarType[UUID]("UUID",
    description = Some(
      "The `UUID` scalar type represents a unique identifier"),
    coerceOutput = (value: UUID, capabilities: Set[MarshallerCapability]) => value.toString,
    coerceUserInput = {
      case s: String ⇒ Right(UUID.fromString(s))
      case u: UUID ⇒ Right(u)
      case _ ⇒ Left(UUIDCoercionViolation)
    },
    coerceInput = {
      case ast.StringValue(s, _) ⇒ toValue(() => UUID.fromString(s), UUIDCoercionViolation)
      case _ ⇒ Left(UUIDCoercionViolation)
    })

  implicit val DateTimeType = ScalarType[DateTime]("DateTime",
    description = Some(
      """
        |The `DateTime` scalar type represents the date and time and is based on
        |the ISO8601/RFC3339 standard. Example of date times: `2010-06-30T01:20+02:00`
        |and `1970-01-01T00:00:00Z`""".stripMargin),
    coerceOutput = (value: DateTime, capabilities: Set[MarshallerCapability]) => RFC3339.format(value),
    coerceUserInput = {
      case s: String ⇒ RFC3339.parseDateTime(s).fold(l => Left(DateTimeCoercionViolation), r => Right(r))
      case d: DateTime ⇒ Right(d)
      case _ ⇒ Left(DateTimeCoercionViolation)
    },
    coerceInput = {
      case ast.StringValue(s, _) => toValue(() => DateTime.parse(s), DateTimeCoercionViolation)
      case _ ⇒ Left(DateTimeCoercionViolation)
    })

  private def toValue[T, V >: ValueCoercionViolation](f: () => T, violation: V): Either[V, T] = {
    Try(f()) match {
      case Success(v) => Right(v)
      case Failure(t) => Left(violation)
    }
  }

}
