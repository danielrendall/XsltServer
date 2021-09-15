package uk.co.danielrendall.xsltserver

import fi.iki.elonen.NanoHTTPD

@main def xsltServer() =
  val port = Option(System.getProperty("port")).map(_.toInt).getOrElse(8080)
  new XsltServerApp(port).start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

