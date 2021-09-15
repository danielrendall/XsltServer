package uk.co.danielrendall.xsltserver

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.{IHTTPSession, Response, newFixedLengthResponse}

import javax.xml.transform.{Templates, TransformerFactory}
import scala.collection.mutable

/**
 * The server itself
 */
class XsltServerApp(port: Int) extends NanoHTTPD(port):

  object Constants:
    val QUIT = "_quit"

  println("Server using port " + port)

  private val tf: TransformerFactory = TransformerFactory.newInstance()

  /**
   * Map of user-visible name of XSLT to the MD5 hash of that XSLT; so if we have the same XSLT bound to a number of
   * different names, we don't waste our time compiling them.
   */
  private val nameToHash: mutable.Map[String, String] =
    new mutable.HashMap[String, String]()

  /**
   * Map of MD5 hash of an XSLT to the compiled Templates for that XSLT
   */
  private val hashToTemplates: mutable.Map[String, Templates] =
    new mutable.HashMap[String, Templates]()

  /**
   * Map of user-visible name of XSLT to and default parameters which should always be passed to that XSLT (which can
   * be added to via query-string params)
   */
  private val nameToDefaultParams: mutable.Map[String, Map[String, String]] =
    new mutable.HashMap[String, Map[String, String]]()

  override def serve(session: IHTTPSession): Response =
    // URI always starts with "/"
    session.getUri.tail.split("/").filterNot(_.isEmpty).toList match {
      case head::tail =>
        if (head == Constants.QUIT) {
          quit()
        } else {
          newFixedLengthResponse(Status.OK, "text/plain", "Hello world")
        }
      case _ =>
        runningMessage()
    }

  private def quit(): Response =
    println("Quitting")
    stop()
    newFixedLengthResponse(Status.OK, "text/plain", "")

  private def runningMessage(): Response =
    newFixedLengthResponse(Status.OK, "text/plain", "XsltServer is running")


