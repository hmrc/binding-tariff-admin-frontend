#!/usr/bin/env bash
sbt compile coverage test it:test coverageOff coverageReport; sbt compile test:compile it:compile