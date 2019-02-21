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
package com.sumologic.sumobot.plugins.pagerduty

import com.sumologic.sumobot.plugins.HttpClientWithTimeOut
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import play.api.libs.json.Json


class PagerDutyEventApi {

  case class PagerDutyEvent(service_key: String, event_type: String, incident_key: String, description: String)

  implicit val pagerDutyEventFormat = Json.format[PagerDutyEvent]

  private val SubmitUrl = "https://events.pagerduty.com/generic/2010-04-15/create_event.json"

  private val client = HttpClientWithTimeOut.client()

  def page(channel: String,
           serviceKey: String,
           description: String) {
    val event = Json.toJson(PagerDutyEvent(serviceKey, "trigger", channel, description))
    val entity = new StringEntity(event.toString(), ContentType.APPLICATION_JSON)
    val post = new HttpPost(SubmitUrl)
    post.setEntity(entity)
    client.execute(post)
  }
}
