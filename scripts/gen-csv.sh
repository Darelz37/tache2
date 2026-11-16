#!/bin/sh
# Générateur de CSV fictif DÉTERMINISTE (mêmes arguments = même fichier, formules sans hasard).
# Usage : sh scripts/gen-csv.sh [N=50000] [INVALIDES=0] > resultats.csv
N=${1:-50000}
INV=${2:-0}
awk -v n="$N" -v inv="$INV" 'BEGIN {
  split("Jean Marie Paul Awa Ibrahim Fatima Samuel Esther Pierre Grace Yannick Nadine Armand Carine Hervé Mireille", P, " ");
  split("EXEMPLE MODELE ESSAI TEST DEMO FICTIF ECHANTILLON SPECIMEN", L, " ");
  print "matricule;nom_complet;date_naissance;moyenne;decision;mention";
  step = (inv > 0) ? int(n / inv) : 0; bad = 0;
  for (i = 1; i <= n; i++) {
    r = i % 20;
    if (r < 14) { d = "ADMIS"; c = 1000 + (i * 97) % 1000 }
    else if (r < 19) { d = "AJOURNE"; c = 300 + (i * 91) % 700 }
    else { d = "ABSENT"; c = -1 }
    avg = ""; men = "";
    if (c >= 0) avg = sprintf("%d.%02d", int(c / 100), c % 100);
    if (d == "ADMIS") { if (c < 1200) men = "PASSABLE"; else if (c < 1400) men = "ASSEZ BIEN"; else if (c < 1600) men = "BIEN"; else men = "TRES BIEN" }
    dob = sprintf("%04d-%02d-%02d", 1998 + (i * 7) % 9, 1 + (i * 5) % 12, 1 + (i * 11) % 28);
    if (step > 0 && i % step == 0 && bad < inv) { dob = "2004-13-45"; bad++ }
    printf("BTS26-%06d;%s %s%d;%s;%s;%s;%s\n", i, P[1 + i % 16], L[1 + i % 8], i, dob, avg, d, men);
  }
}'
