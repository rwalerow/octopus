package octopus

import java.io.IOException

import octopus.dsl._
import octopus.example.domain.{Email, User}
import octopus.syntax._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{AsyncWordSpec, MustMatchers}

import scala.concurrent.Future


class AsyncValidationRulesSpec extends AsyncWordSpec
  with ScalaFutures
  with Fixtures
  with IntegrationPatience
  with MustMatchers {

  private def isEmailUnique(email: Email): Future[Boolean] = Future.successful(email.address.contains("a"))
  private def emailCheckThrowing(email: Email): Future[Boolean] = Future.failed(new IOException())
  private def userThrowNonFatal(user: User): Future[Boolean] = Future.failed(new Exception(Exception_handled_during_validation))
  private def userThrowIOException(user: User): Future[Boolean] = Future.failed(new IOException())
  private def validateUserEither(user: User): Future[Either[String, Boolean]] = Future.successful { user.email match {
    case Email(address) if address == email_Valid.address => Right(true)
    case Email(address) if address == email_Valid_Long.address => Right(false)
    case _ => Left(Email_validated_left_case)
  }}
  private def validateUserOption(user: User): Future[Option[Boolean]] = Future.successful { user.email match {
    case Email(address) if address == email_Valid.address => Some(true)
    case Email(address) if address == email_Valid_Long.address => Some(false)
    case _ => None
  }}

  private val Email_does_not_contain_a = "Email does not contain a"
  private val User_Invalid = "Invalid user"
  private val Exception_handled_during_validation = "Exception handled during validation"
  private val Email_invalid = "Invalid email"
  private val Email_validated_left_case = "Invalid email left case"
  private val User_validated_none_option = "Invalid user none option"

  private val userValidator = Validator[User].async

  "AsyncValidationRules" when {

    "Simple email validator" should {

      implicit val userUniqueEmailValidator = userValidator
        .ruleField('email, isEmailUnique, Email_does_not_contain_a)

      "accept proper email" in {
        user_Valid2.isValidAsync.map(_ mustBe true)
      }

      "reject invalid email" in {

        val expectedValidationError = ValidationError(
          message = Email_does_not_contain_a,
          path = FieldPath(List(FieldLabel('email)))
        )

        user_Valid.isValidAsync.map(_ mustBe false)
        user_Valid.validateAsync.map(_.errors must contain (expectedValidationError))
      }
    }

    "Throwing email validator" should {

      implicit val userUniqueThrowingValidator = userValidator
        .ruleField('email, emailCheckThrowing, Email_does_not_contain_a)

      "throw on validation check" in {
        user_Valid.isValidAsync.failed.map(_  mustBe an [IOException])
      }
    }

    "Catch non fatal rule" should {
      implicit val userCatchNonFatal = userValidator
        .ruleCatchNonFatal(userThrowNonFatal, User_Invalid, e => e.getMessage)

      "fail validation with exception in errors" in {

        val expectedValidationException = ValidationError(
          message = Exception_handled_during_validation
        )

        user_Valid.isValidAsync.map(_  mustBe false)
        user_Valid.validateAsync.map(_.errors must contain (expectedValidationException))
      }
    }

    "Catch only wanted exception" should {
      "catch and handle predicted exception" in {
        implicit val validator = userValidator
          .ruleCatchOnly[IOException](userThrowIOException, User_Invalid, _ => Exception_handled_during_validation)

        user_Valid.isValidAsync.map(_ mustBe false)
      }

      "resolve in error in case of not predicted exception" in {
        implicit val validator = userValidator
          .ruleCatchOnly[IOException](userThrowNonFatal, User_Invalid, _ => Exception_handled_during_validation)

        an [Exception] must be thrownBy user_Valid.isValidAsync.futureValue
      }
    }

    "Work with all 3 cases of either" should {
      implicit val validator = userValidator
        .ruleEither(validateUserEither, Email_invalid)

      "properly validate on Right(true)" in {
        user_Valid.isValidAsync.map(_ mustBe true)
      }

      "properly invalidate on Right(false) case" in {
        val expectedError = ValidationError(
          message = Email_invalid
        )

        user_Valid2.isValidAsync.map(_ mustBe false)
        user_Valid2.validateAsync.map(_.errors must contain (expectedError))
      }

      "properly invalidate with message on Left case" in {
        val expectedError = ValidationError(
          message = Email_validated_left_case
        )

        user_Invalid1.isValidAsync.map(_ mustBe false)
        user_Invalid1.validateAsync.map(_.errors must contain (expectedError))
      }
    }

    "Work with all 3 cases of option" should {
      implicit val validator = userValidator
        .ruleOption(validateUserOption, User_Invalid, User_validated_none_option)

      "properly validate on Some(true)" in {
        user_Valid.isValidAsync.map(_ mustBe true)
      }

      "properly invalidate on Some(false) case" in {
        val expectedError = ValidationError(
          message = User_Invalid
        )
        user_Valid2.isValidAsync.map(_ mustBe false)
        user_Valid2.validateAsync.map(_.errors must contain (expectedError))
      }

      "properly invalidate None case" in {
        val expectedError = ValidationError(
          message = User_validated_none_option
        )
        user_Invalid1.isValidAsync.map(_ mustBe false)
        user_Invalid1.validateAsync.map(_.errors must contain (expectedError))
      }
    }
  }
}
