#!/usr/bin/env bash

/opt/spark/bin/spark-submit \
--class com.tweets.HappiestHashtagsTsBased \
--master local[2] \
/opt/spark/twitter-analytics-assembly-0.0.1-SNAPSHOT.jar \
--triggerIntervalSeconds 200 --topNSamples 10
