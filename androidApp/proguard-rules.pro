# Gemini Nano through ML Kit cannot be checked on the emulator, so ML Kit is kept whole.
# Its components are created by reflection through their no-argument constructors.
-keep class com.google.mlkit.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
