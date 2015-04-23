#!/usr/bin/env bash

CONTEXT=bigdata
PORT=9999
DIR=`dirname $0`

function usage() {
  echo "Usage: $0 [-d <dir>] [-c <context>] [-p <port>]"
  exit 1
}

while getopts c:p:d:? option
do
  case "${option}"
  in
    c) CONTEXT=${OPTARG};;
    p) PORT=${OPTARG};;
    d) DIR=${OPTARG};;
    ?) usage;;
  esac
done

pushd $DIR

echo "Running Blazegraph from `pwd` on :$PORT/$CONTEXT"
java -Dcom.bigdata.rdf.sail.webapp.ConfigParams.propertyFile=RWStore.properties \
     -Dorg.eclipse.jetty.server.Request.maxFormContentSize=20000000 \
     -jar jetty-runner*.jar \
     --port $PORT \
     --path /$CONTEXT \
     blazegraph