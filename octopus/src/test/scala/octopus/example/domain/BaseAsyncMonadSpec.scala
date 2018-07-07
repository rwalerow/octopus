package octopus.example.domain

import octopus.dsl._
import octopus.syntax._
import octopus.{AsyncValidator => _, _}
import org.scalatest.{AsyncWordSpec, MustMatchers}

import scala.concurrent.Future

trait BaseAsyncMonadSpec[M[_]] extends Fixtures with MustMatchers { this: AsyncWordSpec =>

  private val Exception_HandledDuringValidation = "Exception handled during validation"

  def extractValueFrom[A](mval: M[A]): Future[A]

  def validateSimpleEmail(implicit app: App[M]) = {

    val validateEmail: Email => M[Boolean] = (email: Email) => app.pure {
      email.address match {
        case e if e == email_Valid.address => true
        case _ => false
      }
    }

     val expectedValidationException = ValidationError(
       message = Exception_HandledDuringValidation,
       path = FieldPath(List(FieldLabel('email)))
     )

     implicit val userWithEmailValidator: AsyncValidator[M, User] =
       octopus.Validator[User].async[M].rule[Email](_.email, validateEmail, Exception_HandledDuringValidation)

     "accept user With valid email" in {
       extractValueFrom(user_Valid.isValidAsync).map(_ mustBe true)
     }

    "reject user With invalid email" in {
      extractValueFrom(user_Invalid3.isValidAsync).map(_ mustBe false)
    }

    "rejected user errors should contain proper error massage" in {
      extractValueFrom(user_Invalid3.validateAsync).map(_.errors must contain (expectedValidationException))
    }
  }
}