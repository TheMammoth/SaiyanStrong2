# SaiyanStrong release R8/ProGuard rules.
#
# Room, Hilt, and Coil all ship their own consumer-rules.txt inside their AARs
# (verified against the actual AARs in the Gradle cache) and need nothing extra here.
# hilt-work also ships a consumer rule keeping @HiltWorker class names — verified too.
#
# The libraries below either ship NO consumer rules at all, or only a narrow one, so
# their gaps are covered explicitly. These rules were written by decompiling the exact
# AAR/jar versions this project depends on (rather than guessed from memory), since this
# app's dependency versions are newer than this model's training data. They were NOT
# verified against an installed, running release build on a real device this session —
# do a real release install-and-test pass (sign-in, backup, restore, session share) before
# fully trusting minification here, and prefer widening a rule over narrowing one if
# something breaks only in release builds.

# ── kotlinx.serialization ─────────────────────────────────────────────────────
# Official recommended rule set (kotlinx.serialization ships no AAR, so no consumer
# rules of its own reach this app automatically). Protects the @Serializable codegen
# used by our own backup DTOs (data/backup/BackupPayload.kt) and, in principle, any
# other @Serializable classes elsewhere in the dependency graph.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.saiyanstrong.**$$serializer { *; }
-keepclassmembers class com.saiyanstrong.** {
    *** Companion;
}
-keepclasseswithmembers class com.saiyanstrong.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── supabase-kt (auth-kt, storage-kt) ─────────────────────────────────────────
# Ships no consumer-rules.txt/proguard.txt in either AAR (checked directly). Its Auth
# and Storage responses are parsed with kotlinx.serialization internally
# (UserInfo/UserSession/SessionStatus/etc. — see AuthRepositoryImpl.kt), all inside
# library bytecode this app doesn't control, so keep the whole package rather than
# guess at which individual models are JSON-deserialized.
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.jan.supabase.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Ktor client (used by supabase-kt for all network calls) ──────────────────
# Ktor's Android-target artifacts are mostly plain JVM/KMP jars, not AARs, so they
# carry no Android consumer proguard rules either.
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.android.** { *; }

# ── androidx.credentials / googleid (Google Sign-In via Credential Manager) ──
# GoogleIdTokenCredential.createFrom(Bundle) reads named Bundle keys directly in
# library code (not reflection), so it's R8-safe by default — this keep is a cheap
# insurance policy against future library versions that might rely on field names.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ── org.json ───────────────────────────────────────────────────────────────
# Part of the Android platform SDK, not app/library code — R8 never shrinks it.
# CheckForUpdateUseCase.kt and ExerciseMediaRepositoryImpl.kt parse it by explicit
# getString()/getJSONArray() calls (no reflection into our own model classes), so no
# rule is needed on our side either.
