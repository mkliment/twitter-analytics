FROM adoptopenjdk/openjdk14 as builder

WORKDIR /usr/local/src

ENV SCALA_VERSION=2.12.11
ENV SCALA_HOME=/usr/local/share/scala
ENV SBT_VERSION=1.4.0

# TODO: pass twitter keys as ENV

RUN apt-get update &&\
  apt-get install -y curl wget zip ca-certificates &&\
  mkdir -p "${SCALA_HOME}" &&\
  curl -fsL https://downloads.typesafe.com/scala/${SCALA_VERSION}/scala-${SCALA_VERSION}.tgz |\
    tar xz -C "${SCALA_HOME}" --strip-components=1 --exclude="scala-${SCALA_VERSION}/bin/*.bat" && \
  ln -sf ${SCALA_HOME}/bin/* /usr/local/bin/ &&\
  scala -version

RUN \
  curl -L -o sbt-${SBT_VERSION}.deb https://dl.bintray.com/sbt/debian/sbt-${SBT_VERSION}.deb && \
  dpkg -i sbt-${SBT_VERSION}.deb && \
  rm sbt-${SBT_VERSION}.deb && \
  sbt sbtVersion

CMD echo "Use builder image"

WORKDIR /usr/local/twitter-analytics
COPY . /usr/local/twitter-analytics/

# TODO: fix shared streamingContext
RUN echo "Building jar..." &&\
export SBT_OPTS="$SBT_OPTS -Xmx7G -Xss2M -XX:MaxMetaspaceSize=1024M" &&\
sbt "it:testOnly" &&\
sbt "test:testOnly" &&\
sbt "assembly" &&\
chmod -R a+rw /usr/local/twitter-analytics

FROM alpine:3.12

ARG HADOOP_VERSION=3.2.0
ARG HADOOP_VERSION_SHORT=3.2
ARG SPARK_VERSION=3.1.2
ARG AWS_SDK_VERSION=1.11.375

ARG SPARK_PACKAGE=spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION_SHORT}.tgz
ARG SPARK_PACKAGE_URL=https://downloads.apache.org/spark/spark-${SPARK_VERSION}/${SPARK_PACKAGE}
ARG HADOOP_JAR=https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_VERSION}/hadoop-aws-${HADOOP_VERSION}.jar
ARG AWS_SDK_JAR=https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_VERSION}/aws-java-sdk-bundle-${AWS_SDK_VERSION}.jar

# Install dependencies
RUN apk update
RUN apk add openjdk8-jre bash snappy

# Install spark
ENV SPARK_HOME /opt/spark

ADD ${SPARK_PACKAGE_URL} /tmp/
RUN tar xvf /tmp/$SPARK_PACKAGE -C /opt
RUN ln -vs /opt/spark* /opt/spark

ADD $HADOOP_JAR  $SPARK_HOME/jars/
ADD $AWS_SDK_JAR $SPARK_HOME/jars/

# Fix snappy library load issue
RUN apk add libc6-compat
RUN ln -s /lib/libc.musl-x86_64.so.1 /lib/ld-linux-x86-64.so.2

# Cleanup
RUN rm -rfv /tmp/*

# Add Spark tools to path
ENV PATH="{$SPARK_HOME}/bin/:${PATH}"

RUN mkdir -p /opt/scripts
COPY --from=builder /usr/local/twitter-analytics/scripts /opt/scripts
COPY --from=builder /usr/local/twitter-analytics/src/main/resources/log4j.properties $SPARK_HOME/conf/log4j.properties
COPY --from=builder /usr/local/twitter-analytics/target/scala-2.12/twitter-analytics-assembly-0.0.1-SNAPSHOT.jar $SPARK_HOME/

# storage for data
RUN mkdir -p /usr/local/data/prod

ENTRYPOINT ["/bin/bash"]
