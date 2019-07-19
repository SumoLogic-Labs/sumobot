package com.sumologic.sumobot.http_frontend

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, MediaTypes}
import org.apache.commons.io.IOUtils

object StaticResource {
  private[http_frontend] val DefaultContentType = ContentTypes.`text/html(UTF-8)`

  private[http_frontend] val KnownContentTypes = Array(
    (".html", ContentTypes.`text/html(UTF-8)`),
    (".css", ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)),
    (".js", ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)))
}

case class StaticResource(filename: String) {
  private val stream = getClass.getResourceAsStream(filename)

  val contents: Array[Byte] = IOUtils.toByteArray(stream)
  stream.close()

  def contentType: ContentType = {
    val contentType = StaticResource.KnownContentTypes.find(contentType => filename.endsWith(contentType._1)).map(_._2)
    contentType.getOrElse(StaticResource.DefaultContentType)
  }
}
