-keep class app.familygem.Settings, app.familygem.list.FamiliesFragment, app.familygem.list.SubmittersFragment # for R8.fullMode
-keepclassmembernames class app.familygem.Settings, app.familygem.Settings$Tree, app.familygem.Settings$Diagram, app.familygem.Settings$ZippedTree, app.familygem.Settings$Share { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
#-keeppackagenames org.folg.gedcom.model # Gedcom parser lo chiama come stringa eppure funziona anche senza
-keepattributes LineNumberTable,SourceFile # To have the correct line numbers in Android Vitals
-keep class org.apache.xerces.** { *; } # Required by GeoNames to display suggestions of PlaceFinderTextView

#-printusage build/usage.txt # risorse che vengono rimosse
#-printseeds build/seeds.txt # entrypoints
