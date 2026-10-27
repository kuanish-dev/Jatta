# Gemini Nano through ML Kit cannot be checked on the emulator, so ML Kit is kept whole.
# Its components are created by reflection through their no-argument constructors.
-keep class com.google.mlkit.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# LiteRT-LM (Gemma) calls back into its Kotlin classes from native code and ships no rules of its own.
-keep class com.google.ai.edge.litertlm.** { *; }
