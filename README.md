# ContractFrames

The system takes as input a text about different events involving a purchase contract and returns the PROLEG representation.
See the <a href="https://mnavasloro.github.io/ContractFrames/">system description</a> for further reference.

## Installation

```shell
$ sudo apt-get install openjdk-8-jdk
$ sudo apt-get install git
$ sudo apt-get install maven
$ git clone https://github.com/mnavasloro/ContractFrames
$ mvn clean install
```

## Execution

To get help:
```shell
$ java -jar contractf.jar -help
```
The output should be similar to:
```shell
usage: oeg.contractFrames.Main
 -format <arg>   OPTION to choose the format. (proleg), ttl
 -help           COMMAND to show help (Help)
 -logs           OPTION to enable logs
 -nologs         OPTION to disables logs
 -parse <arg>    COMMAND to parse a file
```


To produced PROLEG from a text file talking about a contract (in English):
```shell
$ java -jar contractf.jar -parse
```

To produced RDF from a text file talking about a contract (in English):
```shell
$ java -jar contractf.jar -parse
```





