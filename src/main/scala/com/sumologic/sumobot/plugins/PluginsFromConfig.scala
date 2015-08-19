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
package com.sumologic.sumobot.plugins

import akka.actor.{ActorSystem, Props}
import com.sumologic.sumobot.core.config.ListOfConfigs

import scala.util.Try

object PluginsFromConfig extends PluginCollection {
  override def setup(implicit system: ActorSystem): Unit = {

    val plugins: Map[String, Option[Props]] = ListOfConfigs.parse(system.settings.config, "plugins") {
      (name, pluginConfig) =>
        Try(pluginConfig.getString("class")).toOption.map {
          className =>
            Props(Class.forName(className))
        }
    }

    plugins.filter(_._2.isDefined).foreach {
      tpl =>
        addPlugin(tpl._1, tpl._2.get)
    }
  }
}
