# yt-adskip: plan (v2, efter agy-granskning 2026-11-20)

## Ändringar efter granskning (de här vinner över texten nedan)
- **Inget packageNames-filter i XML:en.** Paketet filtreras i koden. Ett fönsterbyte till ett annat paket ger omedelbart unmute. Andra appar läses aldrig: tjänsten returnerar direkt.
- **maxMuteMs ersätts av "inaktuell signal":** medan ljudet är avstängt kontrolleras det varannan sekund när reklam senast bekräftades. Har ingen bekräftelse kommit på `staleMs` (8 s) slås ljudet på. Lång reklam som fortfarande syns förblir alltså tyst.
- **Mute bara om ljudet inte redan är av** (`isStreamMute`). Användarens egen tystnad lämnas orörd.
- **"Sponsrad"-kort i flödet:** `rules.playerViewIds`. Hittas spelaren söks reklamsignaler bara i dess delträd.
- **Felsökning:** tjänsten håller senaste YouTube-trädet (texter, id:n, bounds) i minnet. MainActivity kopierar det till urklipp. Ingen filsparning.
- Service-XML: `canPerformGestures`, `flagReportViewIds`, `notificationTimeout=50`.
- UI/README: instruktion för "Tillåt begränsade inställningar" (Android 13+, sideload).
- rules.json: knappen "Hämta regler nu", timeout 5 s, tomma strängar filtreras bort, och en ogiltig fil gör att den senast fungerande kopian behålls.
- **Behålls trots förslaget att stryka:** rules.json. användaren ville uttryckligen ha enkla uppdateringar, och en textfix ska gå att göra utan att bygga om.

Mål: en Android-app för eget bruk som (1) stänger av ljudet medan YouTube-appen visar reklam och (2) trycker "Hoppa över" så fort knappen syns. Syftet är att reklam inte ska väcka användaren på natten. Den ska vara lätt att uppdatera när YouTube ändrar sitt gränssnitt.

## Miljö (kontrollerad 2026-11-20)
- Windows, JDK 17 (temurin), Android cmdline-tools + adb, gradle, `gh` inloggad.
- Ingen Android Studio. Bygget sker med Gradle från kommandoraden.

## Arkitektur
1. **AdSkipService** (AccessibilityService, Kotlin)
   - Lyssnar på `com.google.android.youtube`, händelserna `windowContentChanged` och `windowStateChanged`.
   - Reklam känns igen när en nod har en text eller ett view-id från `rules.json`. Matchningen görs på nodens hela trimmade text (lika med eller börjar med), inte på delsträngar, så att "Ad" inte träffar "Advanced" och "Annons" inte träffar en videotitel som bara innehåller ordet.
   - Reklam syns: ljudet stängs av med `ADJUST_MUTE` på STREAM_MUSIC, så att volymnivån sparas, och skip-knappen klickas. Först med `ACTION_CLICK` på närmaste klickbara förälder, annars med `dispatchGesture` mitt på knappens bounds.
   - Reklamen borta: ljudet slås på igen efter `unmuteDelayMs` (1000 ms) om ingen ny reklam har dykt upp under tiden.
   - Failsafes: ljudet slås alltid på efter `maxMuteMs` (120 s), och i `onInterrupt`/`onUnbind`/`onDestroy`. Appen slår bara på ljud som den själv har stängt av (flagga), så användarens egen tystnad lämnas orörd.
   - Klickspärr: högst ett klick per 1,5 s, så att samma knapp inte spammas.
2. **Beslutslogiken som en ren funktion** (`AdState`): in = vilka texter/id:n som syns plus tid, ut = mute, unmute eller klick. Den enhetstestas med JUnit utan att Android behövs.
3. **MainActivity** (minimal)
   - Status: tjänsten på eller av, och en knapp till tillgänglighetsinställningarna.
   - Knapp till inställningen för batterioptimering.
   - Vilken regelversion som används och när den hämtades.
   - **Felsökningsknapp:** spara YouTubes synliga texter och view-id:n till en fil, så att det går att se vad som har ändrats när appen slutar fungera.

## Uppdateringar (två nivåer, ungefär som Zotero-xpi:n med update.json)
1. **Regler utan ny app:** `rules.json` ligger i repot. Appen hämtar den via raw-URL högst en gång per dygn och när tjänsten startar, cachar den och faller tillbaka på en inbyggd kopia. Den innehåller `version`, `adTexts`, `skipTexts`, `adViewIds`, `skipViewIds`, `unmuteDelayMs` och `maxMuteMs`. När YouTube byter text räcker det att ändra filen och pusha. Telefonen hämtar den automatiskt.
2. **Ny app-version:** signerad release-APK på GitHub Releases. På telefonen bevakar **Obtainium** (öppen källkod) repot och installerar nya versioner, så ingen egen uppdateringskod behövs. Alternativet är egen självuppdatering via PackageInstaller och en update.json. Den lämnas bort: mer kod och mer behörighet (`REQUEST_INSTALL_PACKAGES`).
3. `release.ps1`: höj versionCode, bygg med `gradlew assembleRelease`, kör `gh release create vX.Y.Z app-release.apk`.

## Signering
- En release-nyckel (keystore) skapas lokalt, ligger utanför repot (`~\.android-keys\`) och läggs i lösenordshanteraren. Varje uppdatering kräver samma nyckel. Tappas den måste appen avinstalleras och installeras om.

## Repo
- `C:\Dev\yt-adskip`, **publikt** GitHub-repo (`The37thInsight/yt-adskip`). Publikt för att Obtainium och raw-URL:en ska fungera utan token. Det innehåller inga hemligheter och ingen persondata.

## Integritet och behörigheter
- Behörigheter: `BIND_ACCESSIBILITY_SERVICE` och `INTERNET`, det senare bara för att hämta rules.json.
- Tjänsten läser bara YouTube-appen och skickar ingenting någonstans. Felsökningsdumpen sparas lokalt och bara när knappen trycks.

## Tester
- JUnit för `AdState`: dubbelreklam, där ljudet ska förbli avstängt mellan filmerna. maxMute-failsafe. Användarens egen tystnad, som inte ska slås på. Matchning, där "Advanced" inte räknas som reklam och "Hoppa över annonser" räknas som skip.
- Manuellt på telefonen: reklam som går att skippa, reklam som inte går att skippa, två reklamfilmer i rad, helskärm, och att lämna appen mitt i en reklam.

## Avgränsning
- Inget för Google Play. Bara YouTube-appen, inte YouTube Music eller webbläsaren.
- Ingen reklamblockering i nätverket, bara skip och ljud av.

## Öppna frågor
- Stämmer view-id:n `skip_ad_button` och `ad_progress_text` i dagens YouTube-app? Okänt. Felsökningsdumpen visar det på telefonen.
- minSdk 26 (Android 8)?
