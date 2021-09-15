package uk.co.danielrendall.xsltserver

object Exceptions:

  case class RequestTooBigException(bodySize: Int) extends Exception(s"Request of size $bodySize is too big")

  case object NoDocumentSuppliedException extends Exception("No document supplied")

  case class NoTemplatesStoredException(name: String) extends Exception(s"No templates stored for key '$name'")
