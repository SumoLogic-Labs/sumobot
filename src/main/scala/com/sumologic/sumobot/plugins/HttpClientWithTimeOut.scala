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

import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager

object HttpClientWithTimeOut {

  val requestConfig: RequestConfig = RequestConfig.custom
    .setConnectionRequestTimeout(60000)
    .setConnectTimeout(60000)
    .setSocketTimeout(60000)
    .build

  def client(requestConfig: RequestConfig = requestConfig) = {
    val connManager = new PoolingHttpClientConnectionManager()
    connManager.setMaxTotal(200)
    connManager.setDefaultMaxPerRoute(50)

    HttpClientBuilder.create()
      .setDefaultRequestConfig(requestConfig)
      .setConnectionManager(connManager)
      .build()
  }
}
