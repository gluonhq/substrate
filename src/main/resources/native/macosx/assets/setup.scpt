tell application "Finder"
  set theDisk to a reference to (disks whose URL = "DeployVolumeURL")
  open theDisk

  set theWindow to a reference to (container window of disks whose URL = "DeployVolumeURL")

  set current view of theWindow to icon view
  set toolbar visible of theWindow to false
  set statusbar visible of theWindow to false

  -- size of window should fit the size of background
  set the bounds of theWindow to {400, 100, 917, 400}

  set theViewOptions to a reference to the icon view options of theWindow
  set arrangement of theViewOptions to not arranged
  set icon size of theViewOptions to 128
  set background picture of theViewOptions to POSIX file "DeployBackgroundFile"

  -- Create alias for install location
  make new alias file at POSIX file "DeployVolumePath" to POSIX file "DeployInstallLocation" with properties {name:"DeployInstallLocation"}

  set allTheFiles to the name of every item of theWindow
  set xpos to 120
  repeat with theFile in allTheFiles
    set theFilePath to POSIX path of theFile
    set appFilePath to POSIX path of "/DeployTarget"
    if theFilePath is "DeployInstallLocation" then
      -- Position install location
      set position of item theFile of theWindow to {390, 130}
    else if theFilePath ends with appFilePath then
      -- Position application or runtime
      set position of item theFile of theWindow to {120, 130}
    else
      -- Position all other items in a second row.
      set position of item theFile of theWindow to {xpos, 290}
      set xpos to xpos + 150
    end if
  end repeat


  update theDisk without registering applications
  delay 5
  close (get window of theDisk)
end tell
