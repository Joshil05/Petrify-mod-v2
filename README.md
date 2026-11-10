# Petrify (Forge 1.20.1)

Mod, który zamienia graczy i moby w kamienne posągi. Zrobiony pod serwer RP / ARG w klimacie
średniowiecza i fantasy.

> **Uwaga:** ten kod nie był jeszcze kompilowany ani uruchamiany. Środowisko, w którym powstał,
> nie miało dostępu do serwera Forge'a. Przy pierwszym buildzie mogą wyjść drobne błędy kompilacji.
> Wklej je do rozmowy, a poprawię.

## Co robi

- **Zwój Skamienienia** – prawy klik na gracza lub moba zamienia go w kamień. Kucnięcie + prawy klik
  w powietrzu rzuca zaklęcie na siebie. Zwój znika po użyciu (w trybie kreatywnym nie).
- **Zwój Przywrócenia** – prawy klik na posąg przywraca go do życia.
- **Animacja** – kamień wchodzi na postać od stóp do głowy (3 sekundy, `ANIMATION_TICKS` w `PetrifyMod`).
- **Skamieniały nie może**: chodzić, skakać, bić, stawiać ani niszczyć bloków, używać przedmiotów,
  wyrzucać i podnosić rzeczy. Nie dostaje obrażeń (poza `/kill` i pustką).
- Mleko ani inne wyleczenia nie zdejmują efektu.
- **Komendy** (poziom OP 2):
  - `/petrify <cel> [sekundy]` – bez sekund skamienienie trwa do odczarowania
  - `/unpetrify <cel>`

## Budowanie

1. Zainstaluj JDK 17.
2. W folderze projektu wygeneruj wrappera, jeśli go nie ma: `gradle wrapper --gradle-version 8.8`
3. `./gradlew build` (Windows: `gradlew.bat build`)
4. Gotowy plik jest w `build/libs/petrify-1.0.0.jar`. Wrzuć go do `mods/` na serwerze **i** u każdego gracza.

Do testów w IDE: `./gradlew runClient`.

## Przepisy

- Zwój Skamienienia: papier + czarny barwnik (ink sac) + 2 × cobblestone (bez kształtu)
- Zwój Przywrócenia: papier + odłamek ametystu + sztabka złota (bez kształtu)

Na serwerze RP wygodniej jest rozdawać je komendą `/give @p petrify:petrification_scroll`.

## Struktura

| Plik | Rola |
|---|---|
| `effect/PetrifiedEffect` | efekt: blokada ruchu, wyłączenie AI moba, blokada pozycji gracza |
| `util/PetrifyHelper` | nakładanie i zdejmowanie skamienienia, dźwięki, cząsteczki |
| `event/PetrifyEvents` | blokady akcji skamieniałego |
| `client/StoneLayer` + `StoneWaveVertexConsumer` | warstwa renderu i animacja od stóp do głowy |
| `client/ClientEvents` | blokada sterowania po stronie klienta |
