# Changelog

`.\build.ps1 -Publish` nimmt den Abschnitt der jeweiligen Version als Release-Notes; der Launcher zeigt sie unter Einstellungen → Updates.

Die zweite Zahl steigt nur bei großen Updates, sonst zählt die dritte hoch. 1.4.0 bis 1.8.4 heißen deshalb jetzt 1.3.1 bis 1.3.21.

## 1.3.26

The first public release of Lumina for Minecraft 1.21.11, 26.2 and 26.3.

Download **LuminaClient-Setup-1.3.26.exe** below and run it. If Windows says "Windows protected your PC", click "More info", then "Run anyway". After that the launcher updates itself.

## 1.3.25

- Free Look's spectator camera (V) is Lumina's own again instead of Freecam, and now it works on servers too. It glides smoothly, Ctrl flies faster, and your player stays where it is.
- The launcher no longer downloads Freecam.

## 1.3.24

- Free Look's spectator camera (V) works on servers too, so you can use it on your own server with friends. Before, it only flew in your own worlds.

## 1.3.23

- Your Lumina settings are now the same in every version: change something in Lumina 1.21.11 and it is there in 26.2 and 26.3 too. That is everything in the Lumina menu including its keys, your HUD layout, waypoints, capes and skins. The first start puts together what each version had: the settings of the version you played last, plus the waypoints, capes and skins of all of them.
- A second game started next to the first keeps its own Lumina settings, also the same in every version.
- Free Look's spectator camera (V) now runs on Freecam by hashalite and Matt Sturgeon, which the launcher downloads from Modrinth like the other Lumina mods. The Lumina menu stays the same: Spectator Key, Fly Speed, Fly Where You Look and the scroll wheel steer it, it only flies in your own worlds and it ends when you take damage.
- Freecam's own key (F4) is unbound once, so V is the one key for it. You can bind F4 again under Controls.

## 1.3.22

- Capes move like cloth now. Wavey Capes (by tr7zw) comes with Lumina and moves every Lumina cape, yours and other Lumina players': one smooth piece that swings back when you run, follows through when you stop, turn or jump, and settles calmly. It starts without extra wind; Mod Menu → Wavey Capes has its settings.
- Fixed: with Wavey Capes installed, Lumina capes did not show at all.
- Without Wavey Capes, Lumina's own cape moves like cloth too, at any frame rate. The old wave could jitter whenever your speed changed.

## 1.3.21

- Closing the game while on a server with its own resource pack no longer freezes it. Minecraft used to reload every texture and font on the way out, just to quit afterwards; with a second game open that slowed down the whole PC.

## 1.3.20 (was 1.8.3)

- Fixed: signing in to Lumina online failed for everyone, so nobody saw other players' logo, cape or particles. Mojang blocks the check from Lumina's server, so sign-in now uses the player key Mojang gives every account (the one behind signed chat). Your sign-in and that key's private half stay on your PC.
- Launcher: your character on the start page keeps moving while a game runs, as long as the launcher is in front. In the background it still pauses so the game keeps its frames.

## 1.3.19 (was 1.8.2)

- Launcher: the faces of all your accounts load when the launcher starts, not only after you pick one.
- Launcher: the Cosmetics page is ready in the background, so it opens with your skin at once instead of showing the cape alone first.
- In game, all capes and the effect icons are loaded at startup, so the Cosmetics page shows everything right away. Other players' capes show at once too.

## 1.3.18 (was 1.8.1)

- Launcher: while a game runs, a new button next to Running starts a second game with another account. It has its own game folder, so the two don't overwrite each other's settings.
- While the second game runs, the button shows that account's face; click it to stop that game. Running still stops the first one.

## 1.3.17 (was 1.8.0)

- Other Lumina players now see that you use Lumina: the Lumina logo shows in front of your name in their name tags and tab list, and theirs in yours.
- Your cape and particle effect show for other Lumina players too. Capes that ship with Lumina are shared; your own cape files still only show for you.
- Particle effects of other players show within 32 blocks, for the 8 closest players.
- The "Lumina Logo Next To Your Name" setting is gone. The logo is always there.
- Sign-in uses Mojang's own server check, so your Microsoft and Minecraft sign-in never reaches Lumina's server. It stores your Minecraft name, UUID, when you last played and what you wear. No IP addresses, no logs.

## 1.3.16 (was 1.7.7)

- The Lumina logo next to your name is back, in your name tag and in the tab list. It had been missing since 1.7.4.
- New loading bar: a sunken track that a light block fills, in the style of the Lumina menu. It stays sharp at every GUI scale and grows smoothly.
- Launcher: the name tag above your character on the start page is gone.

## 1.3.15 (was 1.7.6)

- All icons in the Lumina menu are now drawn at 32x32 instead of 16x16. They keep their look, with finer edges and clearer details. On a 1080p screen they show pixel for pixel.
- Coordinates, Zoom and Name Tags use the compass, spyglass and name tag from Faithful 32x (https://faithfulpack.net/).
- New Minimap icon: a round map seen from above, with your arrow in the middle, a river, a forest and a red waypoint.
- In very small windows, the menu falls back to the 16x16 icons so they stay sharp.
- The Spotify logo in the player is sharper when no cover is shown.

## 1.3.14 (was 1.7.5)

- On small screens, the Lumina mark stays next to tall inventories like a large chest. It no longer jumps into the corner where JEI has its search bar. Tested with JEI on 1.21.11, 26.2 and 26.3.

## 1.3.13 (was 1.7.4)

- The Lumina mark now shows in every inventory. Before, it was missing in the survival inventory, the crafting table and the furnaces.
- It sits right under the inventory, so it keeps its size next to it at every GUI scale. It also stays clear of the screen edges, where JEI, REI and EMI put their item lists and search bars.
- Texture packs, including the ones servers send, can no longer replace the Lumina logo.

## 1.3.12 (was 1.7.3)

- The Lumina menu (right shift) now matches the launcher. It uses the same font, graphite and off-white, with Minecraft's square blocks and lit edges. Buttons light up their edge on hover, switches and sliders are square, and mod icons sit in inventory slots.
- Waypoints and the HUD editor look the same way.
- Texture packs no longer change the menu, since it no longer uses Minecraft's widget textures.

## 1.3.11 (was 1.7.2)

- New alt manager: click your name in the bottom left corner of the game's main menu to switch accounts or add a Microsoft account, without closing Minecraft.
- New welcome page on the first start, with Microsoft sign-in and a skip button.
- The main menu buttons are clearer, and Options and Quit Game read as well as the large ones.
- Launcher: the status text is gone from the top bar, and particles fade out before they reach it.

## 1.3.10 (was 1.7.1)

- The launcher and the client are now in English.
- The launcher is calmer: the background colors, light effects and glows are gone, so the Lumina logo stands on its own. Buttons and surfaces are plain graphite and off-white.
- Cosmetics: the ring sits behind your character, right under the feet.

## 1.3.9 (was 1.7.0)

- Der Launcher sieht komplett neu aus. Die Seitenleiste ist weg, die Navigation sitzt oben, und alle Seiten liegen in einem durchgehenden Raum. Auf der Startseite fällt ein Lichtstrahl durch den Lumina-Kristall und landet als Farbspektrum auf dem Boden unter deinem Skin. Fährst du über **Spielen**, wird das Licht heller, beim Laden pulsiert es.
- Mods und Shader haben mehr Platz: Mods stehen in zwei Spalten, die Shader-Bilder sind größer. In den Einstellungen steht der Name jeder Gruppe neben ihr.
- Auf der Cosmetics-Seite steht dein Skin jetzt frei auf einer Lichtfläche, das gewählte Cape oder der gewählte Effekt hat einen farbigen Rahmen.
- Alle Funktionen bleiben, wie sie waren. Die Lichteffekte pausieren, wenn du sie nicht siehst oder Minecraft läuft.

## 1.3.8 (was 1.6.5)

- Texture Packs ändern die Icons im Lumina-Menü nicht mehr. Coordinates, Minimap, Name Tags und Zoom zeigen immer die Original-Items von Minecraft, genau wie die anderen Icons daneben gleich bleiben. Rahmen, Buttons und Schrift des Menüs passen sich weiter dem Pack an.

## 1.3.7 (was 1.6.4)

- Hitbox: Die Einstellung „Looked-At Entity Color“ ist weg, sie hatte keine Wirkung.

## 1.3.6 (was 1.6.3)

- Hitbox: Den Ring auf Augenhöhe und den Blickrichtungs-Pfeil kannst du jetzt einzeln ausschalten und umfärben, beim Pfeil auch die Länge einstellen.
- Die Spotify-Mod zeigt im Menü das echte Spotify-Logo, und auch im Player, solange kein Cover da ist.

## 1.3.5 (was 1.6.2)

- Das Lumina-Menü hat neue Icons im Minecraft-Stil. Coordinates, Minimap, Name Tags und Zoom behalten ihre Original-Items.
- Auf der Mods-Seite steht unter der Überschrift nur noch, wie viele Mods installiert sind.
- Brichst du den Start direkt nach **Spielen** ab, bleibt der Launcher jetzt offen.

## 1.3.4 (was 1.6.1)

- Stürzt Minecraft ab, kommt jetzt ein kleines Fenster mit **Log ansehen** und **Log kopieren**. Das Spiel-Log klappt nicht mehr von selbst auf der Startseite auf.
- Beendest du das Spiel selbst, kommt keine Meldung mehr. Solange es noch lädt, stoppt es ohne Nachfrage; nur wenn du in einer Welt oder auf einem Server warst, fragt der Launcher vorher kurz nach.
- Der Launcher ist aufgeräumt: Die erklärenden Untertitel auf den Seiten, in den Einstellungen und in den Fenstern sind weg. Nur „Clean. Minimal. Schnell.“ auf der Startseite bleibt.

## 1.3.3 (was 1.6.0)

- Neu: **Spotify** in den Mods (HUD). Solange die Spotify-App läuft, zeigt ein kleines Widget Cover, Titel, Interpret und Fortschritt. Mit den Tasten springst du zum vorigen oder nächsten Titel, spulst vor und zurück und pausierst. Auf den Balken klicken springt an die Stelle. Klicken kannst du, sobald der Mauszeiger frei ist: im Chat (**T**) oder mit **N**. Dafür ist kein Spotify-Konto im Client und kein Premium nötig; das Widget läuft vorerst nur unter Windows.
- Das Wegpunkt-Menü (**B**) und der HUD-Editor sehen jetzt genauso nach Minecraft aus wie das Lumina-Menü.

## 1.3.2 (was 1.5.0)

- Neu bei **Free Look**: die Zuschauer-Kamera. Drück **V**, und du fliegst wie im Spectator-Modus durch deine Welt und durch Wände, um dir dein Bauwerk anzusehen. Dein Spieler bleibt stehen und ist zu sehen. Steuerung: WASD, Leertaste und Shift, Mausrad stellt das Tempo ein, Strg macht schneller, **V** beendet es. Das geht nur in deinen eigenen Welten, nicht auf Servern. Wenn dein Spieler Schaden bekommt, springt die Kamera zurück. Umschauen beim Laufen liegt weiter auf **Linke Alt**.
- Das Lumina-Menü (Rechte Shift-Taste) ist neu und sieht jetzt aus wie ein Teil von Minecraft: Minecraft-Schrift, Buttons, Regler und Tooltips, echte Items als Symbole, oben Tabs für Mods, Cosmetics und Skin Changer, links die Kategorien. Texture Packs ändern das Menü mit.
- Die Versionen heißen jetzt richtig **Lumina 26.2** und **Lumina 26.3** statt „1.26.2“ und „1.26.3“.

## 1.3.1 (was 1.4.0)

- Neue Startseite im Launcher: Rechts steht dein Skin in 3D, wie in einer Spiel-Lobby. Zieh ihn mit der Maus zur Seite, um ihn zu drehen, mit Schwung dreht er sich weiter. Er atmet, schaut sich um und macht alle paar Sekunden eine Animation (Winken, Hüpfen, Strecken, Umschauen, Drehung); ein Klick auf ihn startet die nächste. Über ihm steht dein Name mit dem Lumina-Logo. Es ist immer der Skin des gewählten Kontos, auch ohne Internet, samt deinem Cape und deinem Partikel-Effekt.
- Neu: **Resourcify** ist fest dabei. Im Ressourcenpaket-Menü im Spiel suchst und lädst du Texture Packs direkt.
- Texture Packs und Shader liegen jetzt in einem gemeinsamen Ordner und erscheinen in allen Lumina-Versionen. Deine vorhandenen Pakete werden beim ersten Start automatisch übernommen, doppelte nur einmal. Welche aktiv sind, stellst du weiter pro Version ein. Pakete für eine andere Minecraft-Version zeigt das Spiel rot als inkompatibel an.
- Neu: der Tab **Shader** auf der Mods-Seite. Durchsuche die Shader von Modrinth mit Vorschaubild für die gewählte Version und lade sie mit einem Klick. Sie gelten für alle Versionen, aktiviert werden sie im Spiel unter Options → Video Settings → Shader Packs. Ist Iris ausgeschaltet, schaltet der Launcher es beim ersten Shader ein.
- CurseForge wurde entfernt. Mods suchst und lädst du über Modrinth; schon installierte Mods bleiben, wo sie sind.
- Capes im LabyMod-Format (22:17) werden jetzt richtig angezeigt, im Spiel und im Launcher. Vorher waren sie abgeschnitten und falsch skaliert.

## 1.3.0

- Neu: Minecraft 26.2 („Lumina 26.2“). Es gibt ein eigenes Profil „Lumina 26.2“ und einen eigenen Spielordner `game-26.2`. Beim ersten Start werden Optionen, Server-Liste und Lumina-Einstellungen aus 1.21.11 übernommen. Alle Lumina-Features und das Performance-Pack laufen dort in den passenden 26.2-Versionen.
- Der Launcher öffnet immer mit der Version, die du zuletzt gespielt hast; vor deinem ersten Spiel mit der neuesten.
- Neu im Launcher: die Seite **Cosmetics** (zwischen Mods und Einstellungen). Cape und Partikel-Effekt wählst du wie im Lumina-Menü. Die Vorschau zeigt deinen Skin in 3D mit dem gewählten Cape (animierte Capes laufen mit) und den Partikeln genau wie im Spiel; sie dreht sich langsam und lässt sich mit der Maus drehen. Ohne Microsoft-Konto trägt Steve die Cosmetics. Eigene Capes einfach auf die Seite ziehen. Die Wahl gilt in allen Lumina-Versionen; während Minecraft läuft, wählst du Cosmetics im Spiel.
- Hauptmenü im Spiel: Singleplayer und Multiplayer sind die beiden großen Buttons, Options und Quit Game sitzen ruhiger nebeneinander darunter. Neuer Glas-Look mit feiner Lichtkante und weichem Schatten; beim Darüberfahren hebt sich der Button leicht an.
- Capes: Thunder Lightning, Star Planet und Celestial Event Astronomy sind jetzt animiert. Turtle Red und Relaxo Snorlax wurden entfernt, das QR-Code-Pattern-Cape heißt jetzt Glass. Die Capes erscheinen in fester Reihenfolge (im Spiel und im Launcher): zuerst die Lumina-Capes, dann die animierten, dann Minecon Enderman, Glass und Cat Pet.
- Sicherungen enthalten jetzt auch deine Mods (alle Profile) sowie Capes und Skins und werden zusätzlich beim ersten Start jeder neuen Version angelegt. Die letzten 10 bleiben. Neu unter Einstellungen → Updates: **Jetzt sichern** und **Wiederherstellen** (vorher wird der aktuelle Stand gesichert).
- Beim Spielstart blendet sich der Launcher aus und kommt nach dem Spielen wieder („Ausblenden“ ist der neue Standard unter Einstellungen → Nach dem Start; „Minimieren“ und „Offen lassen“ gibt es weiter). Nach einem Absturz kommt er immer zurück, damit du das Log siehst.
- Lumina 26.2 und 26.3 starten mit Java 25 und kompakten Objekt-Headern: weniger Speicher, bessere Cache-Nutzung.
- Microsoft-Anmeldung: verständliche Fehlermeldungen statt Fehlercodes (z. B. fehlendes Xbox-Profil, Konto ohne Minecraft). Details stehen in `logs\auth.log`.
- Updater: Kommt ein neueres Update, während schon eines auf den Neustart wartet, ersetzt es das wartende – ein Neustart reicht.
- Zoom und HUD-Größe lassen sich mit dem Touchpad gleichmäßig einstellen, Strg+→ springt in Textfeldern wortweise.

## 1.2.0

- Neu: Minecraft 26.3 („Lumina 26.3“). Der Launcher legt einmalig ein Profil „Lumina 26.3“ an und wählt es aus; zwischen 26.3 und 1.21.11 wechselst du wie gewohnt neben „Spielen“. Alle Lumina-Features, Menüs, HUD, Cosmetics und der Skin Changer funktionieren dort genauso wie in 1.21.11, inklusive Performance-Pack (Sodium, Lithium, Iris …) in den passenden 26.3-Versionen.
- 26.3 hat einen eigenen Spielordner (`game-26.3`), weil 26.3 Welten dauerhaft umwandelt. Beim ersten Start werden deine Optionen, Server-Liste und Lumina-Einstellungen (HUD-Layout, Wegpunkte, Capes, Skins) aus 1.21.11 übernommen; deine 1.21.11-Welten bleiben unangetastet.
- Java 25 (für 26.3) lädt der Launcher automatisch.
- Die Lumina-Profile heißen einfach „Lumina 1.21.11“ und „Lumina 26.3“ (Minecraft 26.3, auch im Spiel so angezeigt) und lassen sich nicht löschen; eigene Profile zeigen ihren Namen und die Version. Die Liste heißt jetzt „Versions“ und ist überarbeitet (Logo bzw. Anfangsbuchstabe vor jedem Profil, Lumina-Versionen und eigene Profile getrennt).
- Auf der Mods-Seite gibt es dieselbe Versions-Auswahl, inklusive „Neues Profil“.
- Neu: **Mods übertragen** (Mods-Seite → Übertragen). Prüft für eine andere Version oder ein anderes Profil, welche deiner Mods es dort gibt (inklusive Abhängigkeiten, auch für manuell hinzugefügte Jars). Gibt es alle, werden sie direkt übertragen, sonst siehst du, welche fehlen, und überträgst die übrigen. Deaktivierte Mods bleiben deaktiviert.
- Der Skin-Kopf unten links zeigt immer deinen aktuellen Skin und aktualisiert sich selbst (nach dem Spielen, alle paar Minuten und wenn du zum Launcher zurückkehrst).
- Das Launcher-Fenster hat eine feste Größe (980 × 620).
- Mods, die eine nur für NeoForge gedachte Abhängigkeit angeben (z. B. Zoomify für 26.3), lassen sich jetzt installieren.

## 1.1.0

- Automatische Updates: Der Launcher lädt neue Versionen im Hintergrund und installiert sie beim Neustart. Mods, Welten und Einstellungen bleiben erhalten; vor jedem Update wird zusätzlich ein Backup deiner Einstellungen angelegt.
- Neuer Skin Changer im Lumina-Menü: Skins hinzufügen (Datei wählen oder aufs Fenster ziehen), in 3D ansehen, Classic/Slim wählen und als echten Minecraft-Skin übernehmen.
- Lumina-Menü aufgeräumt: links nur noch All Mods, Cosmetics und Skin Changer.
- Capes: Das Logo der Lumina-Capes zeigt jetzt nach außen. Mit Elytra siehst du immer die normale Elytra.
- Partikel erscheinen jetzt auch im Inventar und in der Cosmetics-Vorschau.
- Hearts, Sakura und Snow überarbeitet; neue Effekte: Autumn, Butterflies, Fireflies, Bubbles und Starfall. Aura, Halo, Flames und Rainbow wurden entfernt.
- Die Version heißt überall „Lumina 1.21.11“ (Launcher, Titelbildschirm, Fenstertitel, F3).
