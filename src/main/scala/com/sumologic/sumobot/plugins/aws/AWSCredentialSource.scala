package com.sumologic.sumobot.plugins.aws

import com.amazonaws.auth.{BasicAWSCredentials, AWSCredentials}

object AWSCredentialSource {
  def credentials: Option[AWSCredentials] = {
    for (keyId <- sys.env.get(s"AWS_ACCESS_KEY_ID");
         accessKey <- sys.env.get(s"AWS_SECRET_ACCESS_KEY"))
      yield new BasicAWSCredentials(keyId.trim, accessKey.trim)
  }
}


