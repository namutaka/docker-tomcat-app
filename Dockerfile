FROM java:8-jre-alpine

RUN mkdir /app
WORKDIR /app

COPY target/lib      /app/target/lib
COPY target/classes  /app/target/classes
COPY src/main/webapp /app/src/main/webapp

ENV CLASSPATH /app/target/classes:/app/target/lib/*
ENV JAVA_TOOL_OPTIONS -Djava.security.egd=file:/dev/./urandom

EXPOSE 8080 8009

CMD java $JAVA_OPTS Main

