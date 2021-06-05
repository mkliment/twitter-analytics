#!/usr/bin/env bash

# The window duration of windowed DStream must be a multiple of the slide duration of parent DStream
# When setting number of executors be aware about rate limits on developer Twitter API
/opt/spark/bin/spark-submit \
--class com.tweets.HappiestHashtagsWindowBased \
--master local[2] \
/opt/spark/twitter-analytics-assembly-0.0.1-SNAPSHOT.jar \
--triggerIntervalSeconds 150 --windowIntervalSeconds 300 --topNSamples 10
