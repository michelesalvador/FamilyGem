-keep class app.familygem.Armadio, app.familygem.Chiesa, app.familygem.Podio # per R8.fullMode
# https://stackoverflow.com/q/62080165
# https://issuetracker.google.com/issues/153616200
-keepclassmembernames class app.familygem.Armadio, app.familygem.Armadio$Cassetto, app.familygem.Armadio$CassettoDiagram, app.familygem.Armadio$CassettoCondiviso, app.familygem.Armadio$Invio { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
#-keeppackagenames org.folg.gedcom.model # Gedcom parser lo chiama come stringa eppure funziona anche senza
-keepnames class org.slf4j.LoggerFactory
-keep class org.jdom.input.* { *; } # per GeoNames

#-printusage build/usage.txt # risorse che vengono rimosse
#-printseeds build/seeds.txt # entrypoints
