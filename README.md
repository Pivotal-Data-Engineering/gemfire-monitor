#Overview#

GemFire monitor  connects to a cluster via JMX and logs certain metrics 
periodically.  It is built with as few dependencies as possible so that it can
be used by GemFire users who do not use Spring, etc..

GemFire monitor does not 

#Build Instructions#
`mvn package`

This will produce an archive at `target/gemfire-monitor-VERSION-runtime.tar.gz`
which contains everything needed to run the monitor.

#Installation and Configuration#
Unpack the archive on a server that has access to the JMX manager 
(usually this is also a locator).

Output is controlled by the `log4j.properties` file (see instructions in file).
The file includes configurations for sending the output to the console or 
to a file, or both.  More advanced configurations including log rolling can
be configured using log4j 1.2 (see https://logging.apache.org/log4j/1.2/manual.html 
for information)

#Running#
`./gemfire-monitor.sh jmx-host jmx-port [jmx-user jmx-pass]`

The monitor will publish statistics about the cluster every 20s 


__The following conditions will cause a special warning to be logged, which can 
be identified by the presence of the string  "WARN"__ 
* thread count > 2000

Use CTRL-C to stop the monitor if it is running in the foreground or 
`kill` if it is in the background (It's better not to use `kill -9` because
this will not allow gemfire-monitor to close connections before exiting)

#To Do#
* add support for a backup JMX manager
* if this is run inside an app server, revisit the idea of using a non-daemon
thread
* make the interval configurable




