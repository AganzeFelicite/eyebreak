#!/bin/sh
# Build the menu bar app and keep it running: starts at every login, restarts if it crashes.
set -e
cd "$(dirname "$0")"
swiftc -O eyebreak.swift -o eyebreak
P="$HOME/Library/LaunchAgents/io.github.aganzefelicite.eyebreak.plist"
cat > "$P" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>io.github.aganzefelicite.eyebreak</string>
  <key>ProgramArguments</key><array><string>$PWD/eyebreak</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
</dict>
</plist>
PLIST
launchctl bootout "gui/$(id -u)" "$P" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$P"
echo "EyeBreak installed and running. Uninstall: launchctl bootout gui/\$(id -u) $P && rm $P"
