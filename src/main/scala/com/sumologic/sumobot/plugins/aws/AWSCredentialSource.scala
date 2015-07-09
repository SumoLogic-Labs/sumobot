package com.sumologic.sumobot.plugins.aws

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}

object AWSCredentialSource {
  def credentials: Map[String, AWSCredentials] = {
    sys.env.get(s"AWS_ACCOUNTS") match {
      case Some(accountList) =>
        val names: Seq[String] = accountList.split(",").map(_.trim)
        names.flatMap {
          name =>
            val nameUpper = name.toUpperCase
            for (keyId <- sys.env.get(s"${nameUpper}_AWS_ACCESS_KEY_ID");
                 accessKey <- sys.env.get(s"${nameUpper}_AWS_SECRET_ACCESS_KEY"))
              yield name -> new BasicAWSCredentials(keyId.trim, accessKey.trim)
        }.toMap
      case None =>
        Map.empty
    }
  }
}


