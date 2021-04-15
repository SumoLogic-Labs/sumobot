[![Build Status](https://travis-ci.org/SumoLogic/sumobot.svg?branch=master)](https://travis-ci.org/SumoLogic/sumobot) [![codecov.io](http://codecov.io/github/SumoLogic/sumobot/coverage.svg?branch=master)](http://codecov.io/github/SumoLogic/sumobot?branch=master) 

# Sumo Bot

Very early work on a Slack ChatOps bot, written in Akka/Scala. 

### License

Released under Apache 2.0 License.

### Starting Sumo Bot
Sumo Bot supports running on Slack or on a debug HTTP server (but only one at a time). All configuration is stored in `config/sumobot.conf`. You can see sample `sumobot.conf` in [`config/sumobot.conf.example`](https://github.com/SumoLogic/sumobot/blob/master/config/sumobot.conf.example).

#### Running on Slack
You will need a Slack API token. You need to add following lines to your `config/sumobot.conf`:

```
slack {
  api.token = "..."
}
```

#### Running on HTTP server
To run server locally, add following lines to your `config/sumobot.conf`:

```
http {
  host = "localhost"
  port = 8080
}
```

After starting Sumo Bot, you can visit started server at `http://localhost:8080/`.

To run server exposed to external world, change `host` to `0.0.0.0`. For advanced configuration options, see: [`config/sumobot.conf.example`](https://github.com/SumoLogic/sumobot/blob/master/config/sumobot.conf.example).


### [Dev] How to build

To build project in default Scala version:
```
gradlew build
```

To build project in any supported Scala version:
```
gradlew build -PscalaVersion=2.12.12
```


### [Dev] Testing

For testing, change your consumer `pom.xml` or `gradle.properties` to depend on the `SNAPSHOT` version generated.
Make sure, your consumer can resolve artifacts from a local repository.

### [Dev] Managing Scala versions

This project supports multiple versions of Scala. Supported versions are listed in `gradle.properties`.
- `supportedScalaVersions` - list of supported versions (Gradle prevents building with versions from 
outside this list)
- `defaultScalaVersion` - default version of Scala used for building - can be overridden with `-PscalaVersion`

### [Dev] How to release new version
1. Make sure you have all credentials - access to `Open Source` vault in 1Password.
    1. Can login as `sumoapi` https://oss.sonatype.org/index.html
    2. Can import and verify the signing key:
        ```
        gpg --import ~/Desktop/api.private.key
        gpg-agent --daemon
        touch a
        gpg --use-agent --sign a
        gpg -k
        ```
    3. Have nexus and signing credentials in `~/.gradle/gradle.properties`
        ```
        nexus_username=sumoapi
        nexus_password=${sumoapi_password_for_sonatype_nexus}
        signing.gnupg.executable=gpg
        signing.gnupg.keyName=${id_of_imported_sumoapi_key}
        signing.gnupg.passphrase=${password_for_imported_sumoapi_key}
        ```
2. Remove `-SNAPSHOT` suffix from `version` in `build.gradle`
3. Make a release branch with Scala version and project version, ex. `sumobot-1.0.12`:
    ```
    export RELEASE_VERSION=sumobot-1.0.12
    git checkout -b ${RELEASE_VERSION}
    git add build.gradle
    git commit -m "[release] ${RELEASE_VERSION}"
    ```
4. Perform a release in selected Scala versions:
    ```
    ./gradlew build publish -PscalaVersion=2.11.12
    ./gradlew build publish -PscalaVersion=2.12.12
    ./gradlew build publish -PscalaVersion=2.13.3
    ```
5. Go to https://oss.sonatype.org/index.html#stagingRepositories, search for com.sumologic, close and release your repo. 
NOTE: If you had to login, reload the URL. It doesn't take you to the right page post-login
6. Update the `README.md` and `CHANGELOG.md` with the new version and set upcoming snapshot `version` 
in `build.gradle`, ex. `1.0.12-SNAPSHOT`
7. Commit the change and push as a PR:
    ```
    git add build.gradle README.md CHANGELOG.md
    git commit -m "[release] Updating version after release ${RELEASE_VERSION}"
    git push
    ```
