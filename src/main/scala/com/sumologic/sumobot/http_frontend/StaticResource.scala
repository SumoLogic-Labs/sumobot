/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.sumobot.http_frontend

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, MediaTypes}
import org.apache.commons.io.IOUtils

object StaticResource {
  private[http_frontend] val DefaultContentType = ContentTypes.`text/html(UTF-8)`

  case class ContentTypePair(extension: String, contentType: ContentType)

  private[http_frontend] val KnownContentTypes = Array(
    ContentTypePair(".html", ContentTypes.`text/html(UTF-8)`),
    ContentTypePair(".css", ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`)),
    ContentTypePair(".js", ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`))
  )
}

case class StaticResource(filename: String) {
  private val stream = getClass.getResourceAsStream(filename)

  val contents: Array[Byte] = IOUtils.toByteArray(stream)
  stream.close()

  def contentType: ContentType = {
    val contentType = StaticResource.KnownContentTypes.find(contentType => filename.endsWith(contentType.extension))
      .map(contentTypePair => contentTypePair.contentType)
    contentType.getOrElse(StaticResource.DefaultContentType)
  }
}
