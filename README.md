# Family Gem
### _Create your own family tree_

Family Gem is an app for Android designed to manage family trees.\
It's distributed as a freemium app: almost all features are free except one (merging two trees), available with a [Premium subscription](https://www.familygem.app/premium).

## Features
With Family Gem you can:
- Create a family tree from scratch, entering names, dates, places, various events, photos and sources.
- Import an existing family tree through a GEDCOM file and modify it as you want.
- Export the family tree you created (via GEDCOM again) to import in every other genealogy program.
- Share a tree with your relatives, letting them improve it and receiving back the updates. Then you can choose whether accept them or not.
- Export the diagram as PDF or PNG.

Minimum supported version is Android 5 Lollipop (API 21), maximum is Android 16 Baklava (API 36).\
The intent is that data structure respects as much as possible the GEDCOM standard [5.5.1](https://www.familysearch.org/developers/docs/gedcom/) and possibly also [5.5.5](https://www.gedcom.org/gedcom.html).\
Family Gem is strongly based on the library [Gedcom 5 Java](https://github.com/FamilySearch/gedcom5-java) by FamilySearch.

## Limitations
The code provided in this repository should compile and build a working version of Family Gem, but with some limitations:
|Missing|Limitation|
|-|-|
|App signature|You loose saved trees when you install over a signed version|
|Server account|You can't share trees|
|GeoNames account|Place names suggestions probably don't appear|

The code (classes, variables, comments...) was almost all written in Italian (my native language), because I started just as a personal hobby project.\
Obviously everyone expects an English code: it's hard to change at this point but I'm trying to translate all to English.

## Translation
The translation of Family Gem user interface is managed on [Weblate](https://hosted.weblate.org/projects/family-gem/app/).\
Contribution to translation in any language is really appreciated.\
You need a Weblate account to freely work there, but also without account you can make suggestions to already existing translations.

[![Translation status](https://hosted.weblate.org/widgets/family-gem/-/multi-auto.svg)](https://hosted.weblate.org/engage/family-gem/)

## Resources
Official website: www.familygem.app

You can find Family Gem on [Google Play](https://play.google.com/store/apps/details?id=app.familygem).

The APK file is available on:
- [GitHub releases](https://github.com/michelesalvador/FamilyGem/releases)
- [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/app.familygem) (F-Droid repository)
- [Uptodown](https://family-gem.en.uptodown.com)

## Feedback
For questions, bugs, suggestions you can:
- [Open an issue](https://github.com/michelesalvador/FamilyGem/issues)
- Start a conversation on the [Family Gem Google group](https://groups.google.com/g/family-gem) (Google account required)

## License
This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.

## About
Family Gem is written in Java and Kotlin within IntelliJ IDEA and Android Studio.\
Project started on beginning of 2018.\
Author is Michele Salvador, an Italian programmer and genealogy enthusiast.
