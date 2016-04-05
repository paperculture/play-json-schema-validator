package com.eclipsesource.schema

import java.net.URL

import com.eclipsesource.schema.internal._
import com.eclipsesource.schema.internal.validation.VA
import com.eclipsesource.schema.internal.validators.AnyConstraintValidator
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scalaz.{Success, Failure}

trait SchemaValidator {

  implicit class VAExtensions[O](va: VA[O]) {
    def toJsResult: JsResult[O] = va match {
      case Success(s) => JsSuccess(s)
      case Failure(errors) => JsError(errors)
    }
  }

  def validate(schemaUrl: URL, input: => JsValue): JsResult[JsValue] = {
    val schema = for {
      schemaJson <- JsonSource.fromURL(schemaUrl).toOption
      schema <- Json.fromJson[SchemaType](schemaJson).asOpt
    } yield schema

    val result: VA[JsValue] = schema match {
      case None => Failure(Seq(JsPath -> Seq(ValidationError("Schema can not be parsed."))))
      case Some(schemaType) =>
        val id = schemaType match {
          case container: HasId => container.id
          case _ => None
        }
        val context = Context(schemaType, id.orElse(Some(schemaUrl.toString)), Some(schemaUrl.toString))
        process(
          schemaType,
          input,
          context
        )
    }
    result.toJsResult
  }

  def validate[A](schemaUrl: URL, input: => JsValue, reads: Reads[A]) : JsResult[A] = {
    validate(schemaUrl, input).fold(
      valid = readAndValidate(reads),
      invalid = errors => JsError(essentialErrorInfo(errors, Some(input)))
    )
  }

  def validate[A](schemaUrl: URL, input: A, writes: Writes[A]): JsResult[JsValue] = {
    val inputJs = writes.writes(input)
    validate(schemaUrl, inputJs)
  }

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

  def validate(schema: SchemaType)(input: => JsValue): JsResult[JsValue] = {
    val id = schema match {
      case container: HasId => container.id
      case _ => None
    }
    val context = Context(schema, id, id)
    process(
      schema,
      input,
      context
    ).toJsResult
  }

  def validate[A](schema: SchemaType, input: => JsValue, reads: Reads[A]) : JsResult[A] = {
    val result = validate(schema)(input)
    result.fold(
      valid = readAndValidate(reads),
      invalid  = errors => JsError(essentialErrorInfo(errors, Some(input)))
    )
  }

  def validate[A](schema: SchemaType, input: A, writes: Writes[A]): JsResult[JsValue] = {
    val inputJs = writes.writes(input)
    validate(schema)(inputJs)
  }

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

  private[schema] def process(schema: SchemaType, json: JsValue, context: Context): VA[JsValue] = {

    (json, schema) match {
      case (_, schemaObject: SchemaObject)
        if schemaObject.properties.collectFirst { case r@RefAttribute(path, _) if !context.visited.contains(r) => r }.isDefined =>
          val pointer = schemaObject.properties.collectFirst { case r@RefAttribute(path, _) if !context.visited.contains(r) => path }
          RefResolver.resolveRefIfAny(context)(schemaObject) match {
            case None => Results.failureWithPath(
              s"Could not resolve ref ${pointer.getOrElse("")}",
              context.schemaPath,
              context.instancePath,
              json)
            case Some(resolved) =>
              val updatedContext = RefResolver.updateResolutionScope(context, resolved).copy(schemaPath =  JsPath \ pointer.getOrElse("#"))
              Results.merge(
                process(resolved,  json, updatedContext),
                AnyConstraintValidator.validate(json, resolved.constraints.any, updatedContext)
              )
          }
        case (_: JsObject, schemaObject: SchemaObject) if schema.constraints.any.schemaTypeAsString.isDefined =>
          schemaObject.validate(json, context)
        case (_, schemaObject: SchemaObject) if schema.constraints.any.schemaTypeAsString.isEmpty =>
          schemaObject.validate(json, context)
        case (_, c: CompoundSchemaType) =>
          c.validate(json, context)
        case (jsArray: JsArray, schemaArray: SchemaArray) if schemaArray.id.isDefined =>
          schemaArray.validate(jsArray, RefResolver.updateResolutionScope(context, schemaArray))
        case (jsArray: JsArray, schemaArray: SchemaArray) =>
          schemaArray.validate(jsArray, context)
        case (jsArray: JsArray, schemaTuple: SchemaTuple) =>
          schemaTuple.validate(jsArray, context)
        case (jsNumber: JsNumber, schemaNumber: SchemaNumber) =>
          schemaNumber.validate(jsNumber, context)
        case (jsNumber: JsNumber, schemaInteger: SchemaInteger) =>
          schemaInteger.validate(jsNumber, context)
        case (jsBoolean: JsBoolean, schemaBoolean: SchemaBoolean) =>
          schemaBoolean.validate(jsBoolean, context)
        case (jsString: JsString, schemaString: SchemaString) =>
          schemaString.validate(jsString, context)
        case (JsNull, schemaNull: SchemaNull) =>
          schemaNull.validate(json, context)
        case (_, _) if schema.constraints.any.schemaTypeAsString.isEmpty =>
          Success(json)
        case _ =>
          Results.failureWithPath(s"Wrong type. Expected $schema, was ${SchemaUtil.typeOfAsString(json)}.",
            context.schemaPath,
            context.instancePath,
            json)
        }
    }
  }

  object SchemaValidator extends SchemaValidator
