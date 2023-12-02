-keepclassmembernames class app.familygem.Settings, app.familygem.Settings$Tree, app.familygem.Settings$DiagramSettings,
 app.familygem.Settings$TreeSettings, app.familygem.Settings$ZippedTree, app.familygem.Settings$Share { *; }
-keepclassmembers class org.folg.gedcom.model.* { *; }
-keep class org.apache.xerces.** { *; } # Required by GeoNames to display suggestions of PlaceFinderTextView
-keepattributes LineNumberTable,SourceFile # To have the correct line numbers in Android Vitals

# To avoid Fatal Exception java.lang.IllegalStateException TypeToken must be created with a type argument: new TypeToken...
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# To suppress warnings apparently useless
-dontwarn org.apache.xml.resolver.Catalog
-dontwarn org.apache.xml.resolver.CatalogManager
-dontwarn org.apache.xml.resolver.readers.CatalogReader
-dontwarn org.apache.xml.resolver.readers.SAXCatalogReader
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-dontwarn org.slf4j.impl.StaticLoggerBinder
