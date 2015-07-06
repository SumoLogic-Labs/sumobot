FROM java:openjdk-7-jre
ADD /target/sumobot-0.1-SNAPSHOT-bin/ /sumobot/
ENTRYPOINT [ "java", "-classpath", "/sumobot/lib/*", "com.sumologic.sumobot.Main" ]