FROM hseeberger/scala-sbt

COPY . .

RUN sbt build:ps