[![Build Status](https://travis-ci.org/SumoLogic/sumobot.svg?branch=master)](https://travis-ci.org/SumoLogic/sumobot) [![codecov.io](http://codecov.io/github/SumoLogic/sumobot/coverage.svg?branch=master)](http://codecov.io/github/SumoLogic/sumobot?branch=master) [![Stories in Ready](https://badge.waffle.io/SumoLogic/sumobot.svg?label=ready&title=Ready)](http://waffle.io/SumoLogic/sumobot)

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

### [Dev] How to release new version
1. Make sure you have all credentials.
  * Can login as `sumoapi` https://oss.sonatype.org/index.html
  * Have nexus credentials ~/.m2/settings.xml

  ```
  <server>
    <username>sumoapi</username>
    <password>****</password>
    <id>sonatype-nexus-staging</id>
  </server>
  ```
  * Signing key:

  ```
    gpg --import ~/Desktop/api.private.key
    gpg-agent --daemon
    touch a
    gpg --use-agent --sign a

  ```
2. `./mvnw -DperformRelease=true release:prepare`
3. `git clean -i` and remove untracked files, besides release.properties
4. `./mvnw -DperformRelease=true release:perform` (alternative `git checkout HEAD~1 && ./mvnw -DperformRelease=true deploy`)
5. Go to https://oss.sonatype.org/index.html#stagingRepositories, search for com.sumologic and release your repo. NOTE: If you had to login, reload the URL.  It doesn't take you to the right page post-login
6. Update the README.md file with the new version and commit the change
7. Push your commits as PR (`git push origin master:new-branch`)
