# KitKat needs all these classes in the first dex
# At startup
-keep class androidx.**
-keep class com.squareup.picasso.PicassoProvider
-keep class com.google.gson.**, org.apache.commons.io.**
-keep class app.familygem.*
-keep class com.google.android.material.**
# Using the app
-keep class com.squareup.picasso.BitmapHunter { *; }
-keep class org.jdom.input.SAXBuilder, org.jdom.input.TextBuffer, org.jdom.ContentList # for GeoNames
-keep class org.geonames.WebService
-keep class com.theartofdev.edmodo.cropper.*
-keep class com.otaliastudios.zoom.*
-keep class org.jsoup.**
-keep class org.slf4j.helpers.SubstituteLoggerFactory, org.slf4j.helpers.NOPLoggerFactory, org.slf4j.helpers.Util
