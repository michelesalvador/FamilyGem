-keepclassmembernames class app.familygem.Armadio, app.familygem.Armadio$Cassetto, app.familygem.Armadio$CassettoDiagram, app.familygem.Armadio$CassettoCondiviso, app.familygem.Armadio$Invio { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
#-keeppackagenames org.folg.gedcom.model # Gedcom parser lo chiama come stringa..?
-keepnames class org.slf4j.LoggerFactory
-keep class org.jdom.input.* { *; }

#-printusage build/usage.txt
