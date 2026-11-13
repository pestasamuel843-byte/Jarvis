# JARVIS — Android osobný asistent

Natívna aplikácia pre Android v Kotlin + Jetpack Compose. Cieľom je hlasovo ovládaný slovenský asistent s lokálnym AI modelom, nástrojmi a overovaním výsledkov. Aplikácia nepotrebuje Termux, ADB, root ani vlastný API kľúč.

## Aktuálne implementované časti

- Rozhranie s predvoleným jazykom slovenčina (sk-SK).
- Lokálny jazykový model Qwen3 INT4 (~347 MB), ktorý sa po prvom stiahnutí používa priamo v zariadení.
- Hlasová služba s lokálnym detektorom prebudenia Sherpa-ONNX; aktuálny predvolený wake word model rozpoznáva anglické „Hey Jarvis“.
- Rozpoznávanie slovenských príkazov cez Android on-device SpeechRecognizer; dostupnosť offline slovenčiny závisí od jazykového balíka v zariadení.
- Lokálny slovenský mužský TTS hlas, ak ho Android zariadenie poskytuje a má nainštalovaný hlasový balík.
- Lokálna história rozhovorov a pamäť v súkromnom úložisku aplikácie.
- Náhľady aktívnych Android oznámení pre podporované aplikácie, ak používateľ povolí Notification Access. Nie je to úplný prístup k inboxom.
- Vybrané lokálne príkazy pre systém, aplikácie, poznámky, budíky, kalendár, mapy a vyhľadávanie.
- Pracovný priestor s niekoľkými rotovateľnými 3D drôtenými primitívmi a lokálne ukladaným 2D náčrtom.
- Mapový priestor s 3D glóbusom a mapovými dátami; online mapové dlaždice a vyhľadávanie vyžadujú internet.
- CI zostavenie debug APK cez GitHub Actions.

## Hlas a obmedzenia Androidu

Hlasovú službu treba spustiť pri otvorenej aplikácii a udeliť povolenie mikrofónu. Android obmedzuje spúšťanie mikrofónových foreground služieb z pozadia a dostupnosť pri zamknutej obrazovke závisí od verzie systému, nastavení batérie a výrobcu zariadenia. Aplikácia nemôže počúvať, keď je tablet vypnutý, služba zastavená alebo jej Android odoberie prístup. Prvý download wake-word modelu potrebuje internet. Prvé stiahnutie lokálneho Qwen modelu tiež potrebuje internet.

## Osobný agent — plán a hranice

JARVIS má postupne podporovať viacstupňové pracovné postupy: naplánovať kroky, použiť dostupné nástroje, vyžiadať potvrdenie pri externých akciách, overiť výsledok a oznámiť stav. Špecifikácia je v AUTONOMOUS_AGENT_SPEC.md.

Aktuálna aplikácia ešte nevie samostatne ovládať ľubovoľné cudzie aplikácie, publikovať obsah na externých službách ani viesť automatické konverzácie. Také integrácie potrebujú samostatnú implementáciu, testy a oprávnenia. Pri predaji vecí maloletým používateľom má JARVIS pripravovať návrhy; zverejnenie a komunikáciu s neznámymi záujemcami musí skontrolovať a vybaviť rodič alebo zákonný zástupca.

## Zostavenie a inštalácia

1. Otvor GitHub Actions: https://github.com/pestasamuel843-byte/Jarvis/actions
2. Vyber najnovšie úspešné zostavenie Android APK.
3. Stiahni artefakt JARVIS-debug-APK a rozbaľ ZIP.
4. Nainštaluj app-debug.apk do tabletu.

Zelený CI výsledok potvrdzuje kompiláciu a vytvorenie APK, nie testovanie na konkrétnom tablete. Pred používaním treba na tablete overiť mikrofón, offline slovenské rozpoznávanie, TTS, wake word, zamknutú obrazovku a spotrebu batérie.

## Technické údaje

- Kotlin, Jetpack Compose, Java 17, Android SDK 35
- Min SDK: Android 8.0 (API 26)
- Application ID: com.jarvis.assistant
- Model AI: lokálny Qwen3 INT4
- Hlasový wake word: Sherpa-ONNX, aktuálne anglický model „Hey Jarvis“
