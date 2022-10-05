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

import akka.http.scaladsl.model.{ContentType, ContentTypes}
import org.apache.commons.io.IOUtils
import org.fusesource.scalate.TemplateEngine
import org.fusesource.scalate.util.{FileResourceLoader, Resource}

object DynamicResource {
  private val Engine = new TemplateEngine

  Engine.allowReload = false
  Engine.allowCaching = true
  Engine.resourceLoader = new FileResourceLoader {
    override def resource(filename: String): Option[Resource] = {
      val uri = getClass.getResource(filename).toURI.toString
      val stream = getClass.getResourceAsStream(filename)
      val templateText = IOUtils.toString(stream)
      stream.close()

      Some(Resource.fromText(uri, templateText))
    }
  }
}

case class DynamicResource(templateFile: String) {
  DynamicResource.Engine.load(templateFile)

  def contents(templateVariables: Map[String, Any]): String = {
    DynamicResource.Engine.layout(templateFile, templateVariables)
  }

  val contentType: ContentType.NonBinary = ContentTypes.`text/html(UTF-8)`
}
