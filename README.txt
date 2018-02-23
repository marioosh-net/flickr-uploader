flickr-uploader
---------------
command-line mass uploader for Flickr (in Java) using Flickr4Java (https://github.com/boncey/Flickr4Java)

Installation
------------
Build app with Maven (http://maven.apache.org):

mvn package
After successful build .jar is in target directory

cd target
java -jar flickr-uploader.jar

Config
------
You need in Your home directory .flickr-uploader configuration file.
It will be created automatically when you first launch the application.

#flickr-uploader configuration file
apiKey=APIKEY...
secret=SECRET...

Usage
-----
usage: java -jar flickr-uploader.jar
 -d <arg>   directory to upload
 -h         help
 -ns        no save token
 -t <arg>   auth token