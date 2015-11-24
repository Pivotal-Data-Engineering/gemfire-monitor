#Overview#

GemFire monitor  connects to a cluster via JMX
and logs certain metrics periodically.  It is built
with as few dependencies as possible so that it can
be used by GemFire users who do not use Spring, etc..

#To Do#
* add support for a backup JMX manager
* not sure if having a non-daemon thread will be a good thing 
if this runs in the app server
* packaging




