JAVA_HOME="/opt/homebrew/Cellar/openjdk@11/11.0.26/libexec/openjdk.jdk/Contents/Home" ./gradlew -q --console=plain


JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.14/libexec/openjdk.jdk/Contents/Home" ./gradlew -q --console=plain

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew clean build -q 2>&1 | tail -20

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew run 2>&1 | tail -100

# Melissa project
A bee simulator using Java, JavaFX and JaCaMo, a multiagent system.

The hive, bee simulation and behaviours are developed with JaCaMo framework, that allow programming multiagent systems with Jason language. Each agent has goals, believes and actions. 

To see graphically these simulation, JavaFX was used to print each action and development of the simulation.

## How to run
To actually run:
- Get download JDK version 10 (last version yet contains JAVAFX outdated version). 
- Download lastest Eclipse IDE.
- Follow instalation instruction on http://jacamo.sourceforge.net/eclipseplugin/tutorial/ to install Eclipse plugin. Remember set JAVA HOME with jdk10, and check in project properties if are using the jdk10 downloaded.
- Open perspective "JaCaMo" -> Import this project -> right button on melissa.jcm -> Run JaCaMo Application.

## Some prints:
Melissa's running, with each agent report and opened threads and logs.
![Running bee simulation](/img/melissa-running.gif)

Print with more detailed image.
![Print with running simulations, with report for each agents actions](/img/running-stats.png)

Print with graphic informations.
![Detail of graphic information](/img/visual-simulation.png)

Agent geliştirmeleri başladı.
