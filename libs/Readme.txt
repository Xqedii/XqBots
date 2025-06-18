$env:JAVA_HOME = 'C:\Program Files\Java\jdk1.8.0_202'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

mvn package



(IN POWERSHELL)

mvn install:install-file `
  -Dfile="C:\Users\xqedi\Desktop\Bociki\XqBots\libs\MCProtocolLib-1.18.2-1\target\mcprotocollib-1.18.2-1-shaded.jar" `
  "-DgroupId=com.github.GeyserMC" `
  "-DartifactId=MCProtocolLib" `
  "-Dversion=Xqedii" `
  "-Dpackaging=jar"
