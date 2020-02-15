# Family Gem
### Create your own family trees

_Family Gem_ is an app for Android designed to manage family trees.<br>
It's completely free and open source.

Project started on beginning of 2018.<br>
_Family Gem_ is written in Java within Eclipse and Android Studio.<br>
Minimun supported version is Android 4.4 KitKat (API 19), maximum is Android 10 (API 29).<br>
The intent is that data structure respects as much as possible the Gedcom standard, in the latest versions 5.5 and 5.5.1.<br>
It's strongly based on the java [Gedcom parser](https://github.com/FamilySearch/Gedcom) by Family Search.<br>
Author is Michele Salvador, an italian self-taught programmer and genealogy enthusiast.

With _Family Gem_ you can:
- Create a family tree from scratch, entering names, dates, places, various events, photos and sources.
- Import an existing family tree through a Gedcom file and modify it as you want.
- Export the family tree you created (via Gedcom again) to import in every other genealogy program.
- Share a tree with your relatives, letting them improve it and receiving back the updates. Then you can choose whether accept them or not.

There are 2 modules:
- **app** is the actual _Family Gem_ app.
- **lab** is the _Family Lab_ app, a playground used only to develop new features.

The code provided in this repository should compile and build a working version of _Family Gem_, but with some limitations:
|Missing|Limitation|
|-|-|
|App signature|Loose saved trees when install over a signed version|
|Server account|Can't share trees|
|GeoNames "demo" account|Place names suggestions probably don't appear|
|Backup key|Android Backup Service is not available|

The code (classes, variables, comments...) is all written in italian, because I'm italian and I love to write in my native language. I know it's not so kind, because everyone expects an english code, but I started like this as an hobby project and I will continue like this.

Official website: www.familygem.app

The APK file is stored on [Uptodown](https://family-gem.en.uptodown.com).

For questions, problems, suggestions please [open an issue](https://github.com/michelesalvador/FamilyGem/issues).