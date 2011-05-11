# parbench

Visualization tool for webserver concurrency

![Screenshot](https://github.com/downloads/andrewvc/parbench/parbench-snap.png)

## Usage

  Download the [JAR](https://github.com/downloads/andrewvc/parbench/parbench-1.0.0-SNAPSHOT-standalone.jar)
  
  Then run:
    
    # Runs parbench with 50 workers given 100 requests each targeting localhost:9000
    java -jar parbench-1.0.0-SNAPSHOT-standalone.jar 50 100 http://localhost:9000

## License

Copyright (C) 2011 Andrew Cholakian

Distributed under the Eclipse Public License, the same as Clojure.
