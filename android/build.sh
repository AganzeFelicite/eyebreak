#!/bin/sh
# Build build/eyebreak.apk with only the Android SDK command-line tools (no Gradle).
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
JAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
KS=$HOME/.android/debug.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug"

rm -rf build && mkdir -p build/classes build/assets
cp ../messages.txt build/assets/
"$BT"/aapt2 link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml -A build/assets \
  --min-sdk-version 26 --target-sdk-version 34
javac --release 11 -nowarn -cp "$JAR" -d build/classes EyeBreak.java
"$BT"/d8 --min-api 26 --lib "$JAR" --output build $(find build/classes -name '*.class')
(cd build && zip -q unsigned.apk classes.dex)
"$BT"/zipalign -f 4 build/unsigned.apk build/aligned.apk
"$BT"/apksigner sign --ks "$KS" --ks-pass pass:android --out build/eyebreak.apk build/aligned.apk
echo "Built $(pwd)/build/eyebreak.apk"
