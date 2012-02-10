flickr-uploader
===============

command-line mass uploader for [Flickr](http://flickr.com) (in Java)
using [Flickrj](http://flickrj.sourceforge.net/)

Installation
------------
Build app with [Maven](http://maven.apache.org)

<pre><code>mvn assembly:assembly</code></pre>

After successful build .jar is in target directory

<pre><code>cd target
java -jar flickr-uploader.jar</code></pre>

Config
------
You need in Your home directory .flickr-uploader configuration file.<br/> 
It will be created automatically when you first launch the application.

<pre><code>#flickr-uploader configuration file
apiKey=APIKEY...
secret=SECRET...</code></pre>

Usage
-----
<pre><code>usage: java -jar flickr-uploader.jar
 -d &lt;arg&gt;   directory to upload
 -h         help
 -ns        no save token
 -t &lt;arg&gt;   auth token</code></pre>