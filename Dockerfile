FROM runmymind/docker-android-sdk:ubuntu-standalone

ENV JAVA_OPTS "-Xmx4g -Xms4g -Dfile.encoding=UTF-8"

RUN apt-get install gnupg ca-certificates curl
RUN curl -s https://repos.azul.com/azul-repo.key | sudo gpg --dearmor -o /usr/share/keyrings/azul.gpg
RUN echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" | sudo tee /etc/apt/sources.list.d/zulu.list

RUN dpkg --add-architecture i386 && apt-get update -yqq && apt-get install -y \
    zulu19-jdk \
  && apt-get clean

# Download extra add-on that ubuntu-standalone image doesn't already include
RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
    "add-ons;addon-google_apis-google-24"

RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
    "build-tools;30.0.3"

RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-30"

RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-31"


WORKDIR /src
