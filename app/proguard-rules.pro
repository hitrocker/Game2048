# ---------------------------------------------------------------------------
# Project ProGuard / R8 rules (release build has minify + resource shrink on).
# Most libraries (Firebase, Credential Manager, Google Identity, coroutines)
# ship their own "consumer" rules inside their AARs, so this file only adds the
# app-specific safety nets.
# ---------------------------------------------------------------------------

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Compose ViewModels are instantiated reflectively by the framework.
-keepclassmembers class * implements androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep our data/auth model + manager classes intact. They're tiny, and this
# avoids any surprise if Firestore (de)serialization is added later.
-keep class com.hitrocker.game2048.data.** { *; }
-keep class com.hitrocker.game2048.auth.** { *; }

# Firestore: keep classes that use its reflective (de)serialization annotations.
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Strip Android log calls from the release build.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
