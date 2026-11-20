# YT Reklamtyst (yt-adskip)

En Android-app för eget bruk. Den stänger av ljudet medan YouTube-appen visar reklam och trycker på "Hoppa över" så fort det går. Den bygger på Androids tillgänglighets-API, läser bara YouTube-appen och skickar ingenting någonstans. Det enda nätverksanropet hämtar `rules.json` från det här repot.

Den kan inte ligga på Google Play, eftersom Play inte tillåter tillgänglighets-API:t för sådant här. Den installeras som en APK.

## Installera på telefonen
1. Installera **Obtainium** (öppen källkod) och lägg till repo-URL:en `https://github.com/The37thInsight/yt-adskip`. Obtainium installerar och uppdaterar appen från GitHub Releases.
2. Öppna **YT Reklamtyst**.
3. Android 13 och senare: tryck **Appinfo**, sedan ⋮ uppe till höger och **Tillåt begränsade inställningar**. Utan det steget är tjänsten gråmarkerad.
4. Tryck **Slå på tjänsten** och aktivera YT Reklamtyst.
5. Tryck **Batterioptimering** och välj "Tillåt"/"Ingen begränsning" för appen, så att Android inte stänger tjänsten under natten.

## Nattläge
Mellan 22:00 och 06:30 (ändras i appen) stängs ljudet inte av. Volymen sänks i stället på `fadeDownMs` (1,5 s) till `nightLevelPercent` (8 %) av maxvolymen och höjs sedan på `fadeUpMs` (6 s) tillbaka till din volym. Plötslig tystnad och ljud som plötsligt kommer tillbaka väcker. Det gör inte en mjuk sänkning. Ändrar du själv volymen under en reklam lämnar appen den ifred.

## När YouTube har ändrat något (reklam känns inte igen, eller knappen trycks inte)
1. Spela en video med reklam. Medan reklamen visas: öppna YT Reklamtyst via appväxlaren och tryck **Kopiera felsökningsdump**.
2. Klistra in dumpen till Claude eller i en anteckning. Den visar YouTubes texter, view-id:n och var de sitter.
3. Ändra `app/src/main/assets/rules.json`, **höj `version`** och pusha. Det går att göra direkt i GitHubs webbredigerare.
4. Telefonen hämtar de nya reglerna inom ett dygn, eller direkt med **Hämta regler nu**. En ogiltig fil ignoreras och den senast fungerande behålls.

| Fält | Betydelse |
|---|---|
| `adTexts` / `adViewIds` | Visar att reklam pågår (ljudet stängs av) |
| `skipTexts` / `skipViewIds` | Knappen som ska tryckas |
| `playerViewIds` | Spelarens id (`watch_player`, kontrollerat 2026-11-20). Reklam söks bara i spelaren, så att "Sponsrad"-kort i flödet inte räknas. Syns ingen spelare räknas det som ingen reklam. Tomt betyder hela skärmen |
| `unmuteDelayMs` | Väntetid innan ljudet slås på igen, så att det håller tyst mellan två reklamfilmer |
| `staleMs` | Ljudet slås på om skärmen inte har gått att läsa så här länge |

Texterna matchas från början av en nod och fram till en ordgräns. "Hoppa över" träffar alltså "Hoppa över annonser" men inte "Så hoppar du över".

**Inte verifierat:** view-id:na `ad_progress_text`, `ad_badge` och `skip_ad_button` är gissningar. Igenkänningen bygger i stället på texterna "Sponsrad" och "Hoppa över". Ett test 2026-11-20 visade att ljudet stängs av när "Sponsrad" syns i spelaren.

Om du själv höjer volymen under en reklam respekteras det, och ljudet stängs av igen först vid nästa reklam.

## Ny app-version
```powershell
.\release.ps1 0.2.0   # testar, bygger signerad APK, taggar, pushar, skapar GitHub-release
```

### Engångsuppsättning av release-nyckeln
Nyckeln ligger utanför repot. **Spara lösenordet och .jks-filen i lösenordshanteraren.** Varje uppdatering kräver samma nyckel. Tappas den måste appen avinstalleras och installeras om.
```powershell
New-Item -ItemType Directory -Force "$HOME\.android-keys"
keytool -genkeypair -keystore "$HOME\.android-keys\yt-adskip.jks" -alias yt-adskip -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=yt-adskip"
# Skapa sedan $HOME\.android-keys\yt-adskip.properties:
#   storeFile=C:/Users/<du>/.android-keys/yt-adskip.jks
#   password=<lösenordet>
```

## Utveckla
```powershell
.\gradlew.bat test assembleDebug                         # 13 enhetstester för beslutslogik, matchning och regelparsning
adb install -r app\build\outputs\apk\debug\app-debug.apk  # direkt till en USB-ansluten telefon
```
Beslutslogiken ligger i `AdState.kt` och har inga Android-beroenden. Ändra den bara tillsammans med `AdStateTest.kt`.
