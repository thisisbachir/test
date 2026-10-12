# kotlinx.serialization ships its own consumer rules; keep our models explicitly too.
-keep,includedescriptorclasses class com.seventyfivehard.tracker.data.**$$serializer { *; }
-keepclassmembers class com.seventyfivehard.tracker.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.seventyfivehard.tracker.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
