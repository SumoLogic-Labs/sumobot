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
package com.sumologic.sumobot.core.config

import com.typesafe.config.{Config, ConfigException}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object ListOfConfigs {
  def parse[T](config: Config, path: String)(convert: (String, Config) => T): Map[String, T] = {
    Try(config.getObject(path).asScala) match {
      case Success(accounts) =>
        accounts.map {
          obj =>
            val name = obj._1
            name -> convert(name, config.getConfig(path + "." + name))
        }.toMap
      case Failure(e: ConfigException.Missing) =>
        Map.empty
      case Failure(other) =>
        throw other
    }
  }
}
