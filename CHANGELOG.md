# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

## [3.0.0] - 2023-01-23
- Dependency dump
- incl. upgrading to slack-scala-client 0.4.2
- and scalatest 3.2.15
- Removing deprecated `package com.sumologic.sumobot.test`

## [2.0.2] - 2022-10-20
- Fixing the exported dependencies problem started in 2.0.1

## [2.0.1] - 2022-10-20
- Dependency bump, incl. Gradle upgrade
- Fixing `OutgoingImage` logic

## [2.0.0] - 2022-10-17
- Dependency bump
- incl. upgrading to slack-scala-client 0.4.0
- ... and removing quite a few plugins as a consequence

## [1.1.3] - 2022-05-25
- Dependency bump
- Support for `NewChannelTopic`

## [1.1.2] - 2022-05-06
- Dependency bump, incl. slack-scala-client

## [1.1.1] - 2022-02-23
- Dependency bump, incl. Scala versions change

## [1.1.0] - 2022-02-21
- Dropping support for Scala 2.11
- [Upgrading to slack-scala-client 0.3.0](https://github.com/slack-scala-client/slack-scala-client/releases/tag/v0.3.0)
- Dependency bump
 
## [1.0.17] - 2021-10-11
- Basic support for reactions

## [1.0.16] - 2021-10-06
- Support for titles in attachments

## [1.0.15] - 2021-09-10
- Support for sending images in threads

## [1.0.14] - 2021-08-17
- Fix for NullPointerException introduced in 1.0.13

## [1.0.13] - 2021-08-17
- Dependency bump

## [1.0.12] - 2021-05-13
- Dependency bump
- (minor) New method `channelForName()` added

## [1.0.11] - 2021-04-15
- Dependency bump

## [1.0.10] - 2021-04-01
- Dependency bump
- resolve() call on `sumobot.conf`
- Maven clean-up

## [1.0.9] - 2021-02-25
- Dependency bump

## [1.0.8] - 2020-10-29
- Dependency bump

## [1.0.7] - 2020-04-08
- Dependencies version bump

## [1.0.6] - 2020-02-19
- Adding support for reading config from classpath

## [1.0.5] - 2019-12-16
- Upgrading dependencies

## [1.0.3] - 2019-11-14

### Added
- Multiple Scala Version support: `2.11`, `2.12`
- Releasing new versions using Gradle

### Changed
- Updated Gradle configuration as a primary build tool
- Artifact id has a Scala version suffix

## [0.1]

### Added
- Bot framework started.
- Plugin for Jenkins/Hudson.
- A few conversations as examples. 
