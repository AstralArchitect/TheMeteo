# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve Line Numbers and Attributes for debugging stacktraces
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# ===================================================================
# Kotlinx Serialization Rules
# ===================================================================
# Keep @Serializable classes and their generated companion serializers
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *** INSTANCE;
}
-keep,allowobfuscation class *$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
}

# ===================================================================
# Protobuf Lite Rules
# ===================================================================
-keep class com.google.protobuf.** { *; }
-keep class fr.matthstudio.themeteo.proto.** { *; }

# ===================================================================
# Application Data Models & Weather Classes
# ===================================================================
-keep class fr.matthstudio.themeteo.CDNMeteoMetadata { *; }
-keep class fr.matthstudio.themeteo.CDNMetadataBounds { *; }
-keep class fr.matthstudio.themeteo.PolicyUpdateInfo { *; }
-keep class fr.matthstudio.themeteo.data.** { *; }
-keep class fr.matthstudio.themeteo.utilClasses.** { *; }

# ===================================================================
# Ktor Network Client Rules & Warnings
# ===================================================================
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
