-keep class app.familygem.Armadio # per R8.fullMode
-keepclassmembernames class app.familygem.Armadio, app.familygem.Armadio$Cassetto, app.familygem.Armadio$CassettoDiagram, app.familygem.Armadio$CassettoCondiviso, app.familygem.Armadio$Invio { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
#-keeppackagenames org.folg.gedcom.model # Gedcom parser lo chiama come stringa eppure funziona anche senza
-keepnames class org.slf4j.LoggerFactory
-keep class org.jdom.input.* { *; } # per GeoNames

#-printusage build/usage.txt # risorse che vengono rimosse
#-printseeds build/seeds.txt # entrypoints
