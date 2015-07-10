package com.sumologic.sumobot.plugins.aws

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.netflix.config.scala.{DynamicStringProperty, DynamicStringListProperty}

object AWSCredentialSource {
  def credentials: Map[String, AWSCredentials] = {
    DynamicStringListProperty("aws.accounts", List.empty, ",")() match {
      case Some(names) =>
        names.map {
          name =>
            for (keyId <- DynamicStringProperty(s"aws.$name.key.id", null)();
                 accessKey <- DynamicStringProperty(s"aws.$name.key.secret", null)())
              yield name -> new BasicAWSCredentials(keyId.trim, accessKey.trim)
        }.flatten.toMap
      case None =>
        Map.empty
    }
  }
}


