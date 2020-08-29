# Family Gem
### Create your own family tree

Family Gem is an app for Android designed to manage family trees.<br>
It's completely free and open source.

Project started on beginning of 2018.<br>
Family Gem is written in Java within Eclipse and Android Studio.<br>
Minimum supported version is Android 4.4 KitKat (API 19), maximum is Android 10 (API 29).<br>
The intent is that data structure respects as much as possible the GEDCOM standard, in the latest versions 5.5 and 5.5.1.<br>
It's strongly based on the java [Gedcom parser](https://github.com/FamilySearch/Gedcom) by Family Search.<br>
Author is Michele Salvador, an italian self-taught programmer and genealogy enthusiast.

With Family Gem you can:
- Create a family tree from scratch, entering names, dates, places, various events, photos and sources.
- Import an existing family tree through a GEDCOM file and modify it as you want.
- Export the family tree you created (via GEDCOM again) to import in every other genealogy program.
- Share a tree with your relatives, letting them improve it and receiving back the updates. Then you can choose whether accept them or not.

There are 2 modules:
- **app** is the actual Family Gem app.
- **lab** is the Family Lab app, a playground used only to develop new features.

## Limitations
The code provided in this repository should compile and build a working version of Family Gem, but with some limitations:
|Missing|Limitation|
|-|-|
|App signature|You loose saved trees when you install over a signed version|
|Server account|You can't share trees|
|GeoNames "demo" account|Place names suggestions probably don't appear|
|Backup key|Android Backup Service is not available|

The code (classes, variables, comments...) is all almost written in italian, because I'm italian and I love to write in my native language. I know it's not so kind, because everyone expects an english code, but I started like this and it's hard to change at this point.

## Translation
The translation of Family Gem is managed on [Weblate](https://hosted.weblate.org/projects/family-gem/app/).<br>
You need to login to Weblate to freely work there, but also without login you can make suggestions to already existing translations.<br>
Contribution to translate in any language is really appreciated!

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
- Open a topic on the [Family Gem Google group](https://groups.google.com/forum/#!forum/family-gem) (Google account required)
- Directly email to the Google group: family-gem@googlegroups.com (no account required)