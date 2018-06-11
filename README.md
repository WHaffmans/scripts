# Groovy scripts for Orbit Image Analysis

Groovy scripts to automate our classification workflow.

## Running GroovyExecutur directly (from CLI).

```
java -XX:MaxPermSize=150m -Djavax.xml.parsers.DocumentBuilderFactory=com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl -cp "build/libs/orbit-image-analysis.jar;build/libs/lib/*" com.actelion.research.orbit.imageAnalysis.tasks.GroovyExecutor "https://raw.githubusercontent.com/WHaffmans/scripts/master/FolderIterator.groovy"
```



