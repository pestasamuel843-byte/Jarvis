# JARVIS — osobný agent a pracovné postupy

## Cieľ
JARVIS má byť hlasovo ovládaný osobný agent, nie iba konverzačný chatbot. Pri každej úlohe má:
1. Rozpoznať cieľ a rozložiť ho na malé kroky.
2. Vybrať iba nástroje, ktoré sú v aplikácii skutočne implementované a povolené.
3. Pred rizikovou alebo externou akciou zobraziť presný návrh a vyžiadať potvrdenie.
4. Vykonať akciu a overiť výsledok cez dôkaz od nástroja.
5. Oznámiť dokončené kroky, chyby a to, čo ešte vyžaduje používateľa.

## Stavové rozlíšenie
- Návrh: text alebo plán je pripravený, nič nebolo odoslané.
- Čaká na potvrdenie: používateľ musí skontrolovať presný obsah a cieľ.
- Vykonané: nástroj akciu prijal.
- Overené: cieľová služba vrátila potvrdenie alebo výsledok možno nezávisle skontrolovať.
- Zlyhalo: akcia sa nepodarila; JARVIS nesmie tvrdiť opak.

Otvorenie cudzej aplikácie nie je dôkazom odoslania správy ani zverejnenia obsahu.

## Príklad: príprava inzerátu
JARVIS môže lokálne pripraviť návrh inzerátu z údajov, ktoré používateľ poskytne: názov, opis, stav, známe chyby, príslušenstvo a používateľom navrhnutá cena. Nesmie si vymýšľať vlastnosti, stav, záruku ani cenu. Pred zverejnením musí byť zobrazený celý návrh a vyžiadané výslovné potvrdenie.

Keďže používateľ aplikácie je maloletý, zverejnenie, kontakt s neznámymi záujemcami, zdieľanie kontaktných údajov, vyjednávanie, platbu a prípadné stretnutie musí skontrolovať a vybaviť rodič alebo zákonný zástupca. JARVIS môže pripraviť návrh odpovede pre rodiča, ale nesmie samostatne viesť konverzáciu s cudzími záujemcami, zdieľať domácu adresu ani dohadovať osobné stretnutie.

## Príklad: iné úlohy
- Správy: pripraviť text, ukázať príjemcu a presný obsah, odoslať iba po výslovnom potvrdení a iba cez podporované API/akciu.
- Škola: zhrnúť dostupné oznámenia; nikdy netvrdiť, že má celý EduPage klasifikačný záznam, ak ho aplikácia nemá.
- Web: vyhľadávať len cez dostupný online nástroj a uvádzať zdroje; bez internetu jasne oznámiť obmedzenie.
- Súbory a projekty: ukladať iba do používateľom vybraného umiestnenia a po uložení overiť existenciu súboru.
- Zariadenie: rešpektovať systémové povolenia Androidu a neobchádzať ich.

## Aktuálny technický stav
Tento dokument je požiadavková špecifikácia, nie tvrdenie, že všetky pracovné postupy už existujú. Aktuálny projekt má obmedzenú množinu lokálnych príkazov a Android oznámení; nemá všeobecné oprávnenie ovládať ľubovoľné aplikácie ani samostatne publikovať obsah na externých službách. Každá nová integrácia potrebuje samostatnú implementáciu, testy a používateľské povolenia.
