#!/bin/bash
ADB="/opt/android-sdk/platform-tools/adb -s emulator-5554"
P=com.cardiosuivi.app.debug
cd /home/user/TANONB
if [ "$NOINSTALL" != "1" ]; then
  $ADB install -r -t app/build/outputs/apk/debug/app-x86_64-debug.apk | tail -1
  $ADB install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | tail -1
fi
if [ "$NOTIF" = "revoke" ]; then
  $ADB shell pm revoke $P android.permission.POST_NOTIFICATIONS
  $ADB shell pm clear-permission-flags $P android.permission.POST_NOTIFICATIONS user-set user-fixed
else
  $ADB shell pm grant $P android.permission.POST_NOTIFICATIONS 2>/dev/null
fi
D=/sdcard/Android/data/$P/files
$ADB shell mkdir -p $D/ocr_ref
[ "$KEEP" = "1" ] || $ADB shell rm -f $D/recette/journal.tsv
$ADB push /tmp/claude-0/-home-user-TANONB/abab4515-96fe-5cce-8a7e-c22f93855532/scratchpad/ocr_ref/. $D/ocr_ref/ >/dev/null
for cls in "$@"; do
  if [[ "$cls" == *r20_permissions_refusees* ]]; then
    for perm in CAMERA POST_NOTIFICATIONS; do
      $ADB shell pm revoke $P android.permission.$perm
      $ADB shell pm clear-permission-flags $P android.permission.$perm user-set user-fixed
    done
  fi
  if [[ "$cls" == *r16_notification* ]]; then $ADB shell appops set $P SCHEDULE_EXACT_ALARM default; fi
  echo "=== $cls $(date +%H:%M:%S)"
  $ADB shell am instrument -w -r -e class "com.cardiosuivi.app.recette.$cls" $P.test/androidx.test.runner.AndroidJUnitRunner 2>&1 \
    | grep -E "INSTRUMENTATION_STATUS: (test|stack)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_RESULT|^OK \(|FAILURES|Tests run" | grep -vE "^\s+at "
  echo "=== fin $cls $(date +%H:%M:%S)"
done
