#!/bin/bash
cd scheduler-service
java -jar build/libs/scheduler-service-0.0.1-SNAPSHOT.jar > scheduler.log 2>&1
