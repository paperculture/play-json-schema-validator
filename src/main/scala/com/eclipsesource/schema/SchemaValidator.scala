package com.eclipsesource.schema

import java.net.{URL, URLStreamHandler}

import com.eclipsesource.schema.internal.SchemaRefResolver._
import com.eclipsesource.schema.internal.url.UrlResolver
import play.api.data.validation.ValidationError
import play.api.libs.json._

trait HasRefResolver {
  def refResolver: SchemaRefResolver
}

trait CanValidate { self: HasRefResolver =>

  /**
    * Validate the given JsValue against the schema located at the given URL.
    *
    * @param schemaUrl an URL pointing to a schema
    * @param input the value to be validated
    * @return a JsResult holding the validation result
    */
  def validate(schemaUrl: URL, input: => JsValue): JsResult[JsValue] = {

    def buildContext(schema: SchemaType): SchemaResolutionContext = {
      val id = schema match {
        case container: HasId => container.id
        case _ => None
      }
      new SchemaResolutionContext(refResolver,
        new SchemaResolutionScope(schema, id.orElse(Some(schemaUrl.toString)), Some(schemaUrl.toString)))
    }

    for {
      schema <- JsonSource.schemaFromUrl(schemaUrl)
      result <- schema.validate(input, buildContext(schema)).toJsResult
    } yield result
  }

  /**
    * Validate the given JsValue against the schema located at the given URL
    * and convert the result via the specified Reads instance in case
    * it has been successful.
    *
    * @param schemaUrl an URL pointing to a schema
    * @param input the value to be validated
    * @return a JsResult holding the validation result
    */
  def validate[A](schemaUrl: URL, input: => JsValue, reads: Reads[A]) : JsResult[A] = {
    validate(schemaUrl, input).fold(
      valid = readAndValidate(reads),
      invalid = errors => JsError(essentialErrorInfo(errors, Some(input)))
    )
  }

  /**
    * Convert the given value via the specified Writes instance to a JsValue
    * and validate it against the schema located at the given URL.
    *
    * @param schemaUrl an URL pointing to a schema
    * @param input the value to be validated
    * @return a JsResult holding the valid result
    */
  def validate[A](schemaUrl: URL, input: A, writes: Writes[A]): JsResult[JsValue] = {
    val inputJs = writes.writes(input)
    validate(schemaUrl, inputJs)
  }

  /**
    * Convert the given value via the specified Format instance to a JsValue,
    * validate it against the schema at the given URL, and convert it back.
    *
    * @param schemaUrl an URL pointing to a schema
    * @param input the value to be validated
    * @return a JsResult holding the valid result
    */
  def validate[A: Format](schemaUrl: URL, input: A): JsResult[A] = {
    val writes = implicitly[Writes[A]]
    val reads = implicitly[Reads[A]]
    validate(schemaUrl, input, writes).fold(
      valid = readAndValidate(reads),
      invalid = errors => JsError(essentialErrorInfo(errors, None))
    )
  }

  //
  // --
  //

  /**
    * Validate the given JsValue against the given schema.
    *
    * @param schema the schema to validate against
    * @param input the value to be validated
    * @return a JsResult holding the valid result
    */
  def validate(schema: SchemaType)(input: => JsValue): JsResult[JsValue] = {
    val id = schema match {
      case container: HasId => container.id
      case _ => None
    }
    val context = new SchemaResolutionContext(refResolver, new SchemaResolutionScope(schema, id, id))
    schema.validate(
      input,
      context
    ).toJsResult
  }

  /**
    * Validate the given JsValue against the schema and convert the result
    * via the specified Reads instance in case it has been successful.
    *
    * @param schema the schema to validate against
    * @param input the value to be validated
    * @return a JsResult holding the validation result
    */
  def validate[A](schema: SchemaType, input: => JsValue, reads: Reads[A]) : JsResult[A] = {
    val result = validate(schema)(input)
    result.fold(
      valid = readAndValidate(reads),
      invalid  = errors => JsError(essentialErrorInfo(errors, Some(input)))
    )
  }

  /**
    * Convert the given value via the specified Writes instance to a JsValue
    * and validate it against the schema.
    *
    * @param schema the schema to validate against
    * @param input the value to be validated
    * @return a JsResult holding the valid result
    */
  def validate[A](schema: SchemaType, input: A, writes: Writes[A]): JsResult[JsValue] = {
    val inputJs = writes.writes(input)
    validate(schema)(inputJs)
  }

  /**
    * Convert the given value via the specified Format instance to a JsValue,
    * validate it against the schema at the given URL, and convert it back.
    *
    * @param schema the schema to validate against
    * @param input the value to be validated
    * @return a JsResult holding the valid result
    */
  def validate[A: Format](schema: SchemaType, input: A): JsResult[A] = {
    val writes = implicitly[Writes[A]]
    val reads = implicitly[Reads[A]]
    val inputJs = writes.writes(input)
    val result = validate(schema)(inputJs)
    result.fold(
      valid = readAndValidate(reads),
      invalid = errors => JsError(essentialErrorInfo(errors, Some(inputJs)))
    )
  }

  private def readAndValidate[A](reads: Reads[A]): JsValue => JsResult[A] = json =>
    reads.reads(json) match {
      case JsSuccess(success, _) => JsSuccess(success)
      case JsError(errors) => JsError(essentialErrorInfo(errors, Some(json)))
    }

  private def essentialErrorInfo(errors: Seq[(JsPath, Seq[ValidationError])], json: Option[JsValue]): Seq[(JsPath, Seq[ValidationError])] = {

    def dropObjPrefix(path: String): String = {
      if (path.startsWith("/obj")) {
        "/" + path.substring(5)
      } else {
        path
      }
    }

    errors.map { case (path, validationErrors) =>
      path ->
        validationErrors.map(err =>
          err.args.size match {
            case 0 => ValidationError(err.message,
              Json.obj(
                "schemaPath" -> "n/a",
                "instancePath" -> dropObjPrefix(path.toString()),
                "value" -> json.fold[JsValue](Json.obj())(identity),
                "errors" -> Json.obj()
              )
            )
            case _ => err
          }
        )
    }
  }
}

/**
  * The schema validator.
  *
  * @param refResolver the reference resolver
  */
case class SchemaValidator(val refResolver: SchemaRefResolver = new SchemaRefResolver)
  extends CanValidate with HasRefResolver {


  /**
    * Add a URL stream handler that is capable of handling the specified protocol.
    *
    * @param protocolEntry a tuple mapping the protocol type to the respective handler
    * @return a new validator instance
    */
  def addUrlHandler(protocolEntry: (String, URLStreamHandler)): SchemaValidator =
    copy(refResolver =
      refResolver.copy(resolverFactory =
        refResolver.resolverFactory.addUrlHandler(protocolEntry)))

  /**
    * Add a URL resolver (which is just convenience wrapper around an URLStreamHandler)
    * that is capable of handling the specified protocol.
    *
    * @param urlResolver the UrlResolver to be added
    * @return a new validator instance
    */
  def addUrlResolver(urlResolver: UrlResolver): SchemaValidator =
    copy(refResolver =
      refResolver.copy(resolverFactory =
        refResolver.resolverFactory.addUrlResolver(urlResolver)))
}
