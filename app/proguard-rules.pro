-keepclassmembernames class app.familygem.Settings, app.familygem.Settings$Tree, app.familygem.Settings$DiagramSettings,
 app.familygem.Settings$TreeSettings, app.familygem.Settings$ZippedTree, app.familygem.Settings$Share { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
-keepattributes LineNumberTable,SourceFile # To have the correct line numbers in Android Vitals
# Suppresses apparently useless warnings
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep class org.apache.xerces.** { *; } # Required by GeoNames to display suggestions of PlaceFinderTextView
# Warnings derived by using xerces:xercesImpl
-dontwarn org.apache.xml.resolver.Catalog
-dontwarn org.apache.xml.resolver.CatalogManager
-dontwarn org.apache.xml.resolver.readers.CatalogReader
-dontwarn org.apache.xml.resolver.readers.SAXCatalogReader
