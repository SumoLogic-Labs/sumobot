[![Build Status](https://travis-ci.org/SumoLogic/sumobot.svg?branch=master)](https://travis-ci.org/SumoLogic/sumobot) [![codecov.io](http://codecov.io/github/SumoLogic/sumobot/coverage.svg?branch=master)](http://codecov.io/github/SumoLogic/sumobot?branch=master) [![Stories in Ready](https://badge.waffle.io/SumoLogic/sumobot.svg?label=ready&title=Ready)](http://waffle.io/SumoLogic/sumobot)

# Sumo Bot

Very early work on a Slack ChatOps bot, written in Akka/Scala. 

### License

Released under Apache 2.0 License.

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
