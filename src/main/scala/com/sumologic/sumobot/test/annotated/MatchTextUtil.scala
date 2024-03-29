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
package com.sumologic.sumobot.test.annotated

import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.matching.Regex

/**
 * @author Chris (chris@sumologic.com)
 */
trait MatchTextUtil extends AnyWordSpecLike {

  def shouldMatch(regex: Regex, text: String): Unit = {
    if (!doesMatch(regex, text)) {
      fail(s"$regex did not match $text but should")
    }
  }

  def shouldNotMatch(regex: Regex, text: String): Unit = {
    if (doesMatch(regex, text)) {
      fail(s"$regex matched $text but should not")
    }
  }

  private def doesMatch(regex: Regex, text: String): Boolean = {
    regex.pattern.matcher(text).find()
  }
}
