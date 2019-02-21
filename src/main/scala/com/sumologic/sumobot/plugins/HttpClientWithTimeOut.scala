package com.sumologic.sumobot

import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder

object HttpClientWithTimeOut {

  val requestConfig: RequestConfig = RequestConfig.custom
    .setConnectionRequestTimeout(60000)
    .setConnectTimeout(60000)
    .build

  def client(requestConfig: RequestConfig = requestConfig) = {
    HttpClientBuilder.create()
      .setDefaultRequestConfig(requestConfig)
      .build()
  }
}
