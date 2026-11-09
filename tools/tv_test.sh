#!/bin/bash
# Parcours automatique de Glive sur l'émulateur Android TV, avec captures d'écran.
set -x
OUT=shots
mkdir -p $OUT
PKG=com.anis.glive
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; }
key() { adb shell input keyevent "$@"; sleep 0.6; }

adb install -r Glive.apk
adb logcat -c
adb shell am start -n $PKG/.ui.MainActivity --es server http://10.0.2.2:8000 --es user test --es pass test
shot 01_demarrage 12
shot 02_accueil 15
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_DOWN; shot 03_accueil_rangees 2
key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; shot 04_menu_ouvert 1
key KEYCODE_DPAD_DOWN; shot 05_direct 4
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN; shot 06_groupes 3
key KEYCODE_DPAD_RIGHT; shot 07_chaines 4
key KEYCODE_DPAD_CENTER; shot 08_lecture 10
key KEYCODE_DPAD_DOWN; shot 09_zapping 6
key KEYCODE_DPAD_CENTER; shot 10_liste_chaines 3
key KEYCODE_BACK; key KEYCODE_BACK; shot 11_retour 3
key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_DOWN; shot 12_guide 5
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; shot 13_guide_programmes 4
key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_DOWN; shot 14_films 8
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_DOWN; shot 15_films_rangees 3
key KEYCODE_DPAD_CENTER; shot 16_fiche_film 5
key KEYCODE_DPAD_CENTER; shot 17_lecture_film 10
key KEYCODE_BACK; key KEYCODE_BACK; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_DOWN; shot 18_series 6
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER; shot 19_fiche_serie 6
key KEYCODE_BACK; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN; shot 20_reglages 4

adb shell dumpsys gfxinfo $PKG > $OUT/gfxinfo.txt
adb logcat -d -v time > $OUT/logcat_full.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|$PKG" $OUT/logcat_full.txt | head -300 > $OUT/logcat_app.txt
adb shell pidof $PKG > $OUT/pid.txt || echo "APP MORTE" > $OUT/pid.txt
exit 0
