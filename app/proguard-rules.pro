-keep class app.familygem.Settings, app.familygem.ChurchFragment, app.familygem.ListOfAuthorsFragment # for R8.fullMode
-keepclassmembernames class app.familygem.Settings, app.familygem.Settings$Tree, app.familygem.Settings$Diagram, app.familygem.Settings$ZippedTree, app.familygem.Settings$Share { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
#-keeppackagenames org.folg.gedcom.model # Gedcom parser lo chiama come stringa eppure funziona anche senza
-keepattributes LineNumberTable,SourceFile # per avere i numeri di linea corretti in Android vitals

#-printusage build/usage.txt # risorse che vengono rimosse
#-printseeds build/seeds.txt # entrypoints
