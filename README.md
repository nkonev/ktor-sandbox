# Building
```
./gradlew clean build
```

# Running
```
cd /tmp && java -jar ~/javaWorkspace/ktor-sandbox/build/libs/ktor-sandbox.jar
```

# Querying
```
curl -i -X POST -H "Accept: application/json" 'http://localhost:8080/customer'
```