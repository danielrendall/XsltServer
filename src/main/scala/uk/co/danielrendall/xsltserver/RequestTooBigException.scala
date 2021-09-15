package uk.co.danielrendall.xsltserver

case class RequestTooBigException(bodySize: Int) extends Exception(s"Request of size $bodySize is too big")
