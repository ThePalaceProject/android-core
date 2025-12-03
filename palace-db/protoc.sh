#!/bin/sh

exec protoc \
--java_out=lite:src/main/java \
src/main/protobuf/DBSerialization.proto
