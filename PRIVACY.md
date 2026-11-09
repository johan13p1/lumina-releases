# Privacy

Lumina is a free, open source Minecraft client. This page lists what it sends, and where.

## Lumina Online

Lumina Online shows your cape, particle effect and the Lumina mark to other Lumina players, and theirs to you. For this the game sends to Lumina's server (a Cloudflare Worker run by the Lumina project):

- your Minecraft name and UUID
- the cape and particle effect you wear
- a signature made with the player key Mojang gives your account, which proves the account is yours
- the UUIDs of players around you, to find out which of them use Lumina

The server stores your Minecraft name, UUID, what you wear and when you last played. It stores no IP addresses and keeps no logs.

While its window is open, the launcher asks Lumina's server every 30 seconds how many players are online right now, to show that number at the top. This request contains nothing about you.

**You can turn it off**: in the installer, or in game under Right Shift > Lumina Settings > Lumina Online. When it is off, nothing is sent to Lumina's server.

## Other services

To start and update the game, the launcher also uses:

- **Microsoft, Xbox and Mojang** for sign-in, the game files and skins ([Microsoft privacy statement](https://privacy.microsoft.com/privacystatement))
- **Modrinth** for the mods that come with Lumina and the mods and shaders you pick ([Modrinth privacy policy](https://modrinth.com/legal/privacy))
- **GitHub** to check for and download new versions of Lumina ([GitHub privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement))

Your Microsoft sign-in is stored encrypted on your PC and is never sent to Lumina's server. The Spotify widget reads what plays on your PC through Windows; nothing about it leaves your PC.

## Contact

Questions, or a request to remove your entry from Lumina's server: [GitHub Issues](https://github.com/johan13p1/lumina-releases/issues).
