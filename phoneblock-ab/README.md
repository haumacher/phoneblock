# phoneblock-ab - Der PhoneBlock-Anrufbeantworter

Das Blocklist-Telefonbuch wird immer größer. Mittlerweile hat es die 10.000 Nummmenmarke geknackt. Ein Telefonbuch in der 
Fritz!Box kann aber je nach Modell nur höchstens 3000 bis 4000 Nummern fassen. Die Lösung ist der [PhoneBlock-Anrufbeantworter](https://phoneblock.net/phoneblock/anrufbeantworter/).

Den PhoneBlock-Anrufbeantworter meldest du an Deiner Fritz!Box (oder einem beliebigen anderen VOIP-fähigen Internetrouter) 
als Telefoniegerät "Telefon mit oder ohne Anrufbeantworter" an. Bei einem Anruf checkt der PhoneBlock-AB blitzschnell, ob die 
anrufende Nummer auf der Blocklist steht und geht dann ran. Der Vorteil ist, dass die **Blocklist immer 100% aktuell** ist, da es 
keinen nächtlichen Update-Lauf mehr gibt.

Das Witzige ist, dass der PhoneBlock-Anrufbeantworter deine unerwünschten Anrufer in ein richtiges Gespräch verwickeln und sie so
eine kleine Weile davon abhalten kann, andere zu nerven. Wie das geht? Wenn PhoneBlock einen Anruf annimmt, wird zuerst eine 
Begrüßungsmeldung abgespielt. Danach wartet PhoneBlock, bis das Gegenüber etwas sagt und stellt danach vollkommen sinnlose 
Rückfragen. Eine professionelle Telfonmarketingfachkraft kann sich so minutenlang mit PhoneBlock unterhalten.

## Ohne eigenen Computer direkt loslegen
Um den [PhoneBlock-Anrufbeantworter](https://phoneblock.net/phoneblock/anrufbeantworter/) zu nutzen, benötigst Du keine 
Zusatzhardware. Du kannst die offizielle Version auf phoneblock.net verwenden und diesen Cloud-AB direkt an Deiner Fritz!Box
anmelden. Natürlich kannst Du den Anrufbeantworter auch lokal bei Dir betreiben, das erfordert allerdings ein gutes Maß an 
technischen Vorkenntnissen - einfach weiterlesen.

## Start as Docker container
If you don't want to build the code your own, you can use a [pre-built docker container](https://hub.docker.com/r/phoneblock/answerbot)
to start the answerbot locally. Check out the details on the docker page.

## Howto build and run

You need a Java 17 JDK, you can test whether it is in your path:
```
java -version
```
This should report something like the following:
```
openjdk version "17.0.12" 2024-07-16
```

Git and a Maven build tool are required for building, you can instead download a pre-compiled version from GitHub.

### Clone the repo
```
git clone https://github.com/haumacher/phoneblock.git
cd phoneblock
```

### Build the code

Assuming, you are in the main directory cloned from GitHub, type
```
mvn -am --projects de.haumacher:phoneblock-ab install
```

After lots of output, it should report:
```
[INFO] --- maven-install-plugin:3.0.1:install (default-install) @ phoneblock-ab ---
[INFO] Installing .../phoneblock/phoneblock-ab/pom.xml to .../.m2/repository/de/haumacher/phoneblock-ab/<version>/phoneblock-ab-<version>.pom
[INFO] Installing .../phoneblock/phoneblock-ab/target/phoneblock-ab-<version>.jar to .../.m2/repository/de/haumacher/phoneblock-ab/<version>/phoneblock-ab-<version>.jar
[INFO] Installing .../phoneblock/phoneblock-ab/target/phoneblock-ab-<version>-jar-with-dependencies.jar to .../.m2/repository/de/haumacher/phoneblock-ab/<version>/phoneblock-ab-<version>-jar-with-dependencies.jar
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for PhoneBlock Aggregator <version>:
[INFO] 
[INFO] PhoneBlock Aggregator .............................. SUCCESS [  0.446 s]
[INFO] PhoneBlock shared code ............................. SUCCESS [  2.104 s]
[INFO] PhoneBlock Anrufbeantworter ........................ SUCCESS [  2.714 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Now you have a runnable JAR in `phoneblock-ab/target/phoneblock-ab-<version>-jar-with-dependencies.jar`.

## Create WAV files for playback by the answerbot
Create those files in the directory `phoneblock-ab/conversation/*/PCMA`. Have a look at the directory layout of [conversation](conversation) for example. Be sure to use the WAV audio file format and save these files with 8bit PCMA (a-law encoding) which is required for RTP audio streaming.

## Configure the answerbot
Copy the configuration template to your home directory
```
cp phoneblock-ab/.phoneblock.template ~/.phoneblock
```

Fill out the missing information. You have to enter at least
* `via-addr`
* `user`
* `passwd`
* `phoneblock-username`
* `phoneblock-password`
* `conversation`

### Start the answerbot
You can start the answer bot with the following command. This assumes that you have built the code youself. If you downloaded a pre-compiled version, you have to adjust the file name and its path:
```
java -jar phoneblock-ab/target/phoneblock-ab-<version>-jar-with-dependencies.jar
```
