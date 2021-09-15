package uk.co.danielrendall.xsltserver

import java.io.{InputStream, OutputStream}
import scala.annotation.tailrec

/**
 * Utilities for streams.
 *
 */
object StreamUtils:

  private val bufferSize: Int = 16384

  def copy(is: InputStream, os: OutputStream, bytesToCopy: Int): Unit =
    val buf = Array.ofDim[Byte](bufferSize)
    @tailrec
    def copyRec(bytesToRead: Int): Unit = {
      if (bytesToRead > 0) {
        val bytesRead = is.read(buf, 0, bufferSize)
        os.write(buf, 0, bytesRead)
        copyRec(bytesToRead - bytesRead)
      }
    }
    copyRec(bytesToCopy)
    os.flush()

