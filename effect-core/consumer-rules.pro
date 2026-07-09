# effect-core consumer rules.
#
# Keep the public SDK surface readable for apps that use reflection, logs,
# or mixed Java/Kotlin call sites, while still allowing R8 to remove unused APIs.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# EffectStageView may be inflated from XML by class name.
-keep public class com.chat.effect.EffectStageView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectManager { public *; }
-keep,allowshrinking,allowoptimization public class com.chat.effect.DownloadClient { public *; }
-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectLog { public *; }

-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectResource { public *; }
-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectType { public *; }
-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectType$* { public *; }
-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectChannel { public *; }
-keep,allowshrinking,allowoptimization public class com.chat.effect.EffectChannel$* { public *; }
-keep,allowshrinking,allowoptimization public enum com.chat.effect.EffectPriority { public *; }

-keep,allowshrinking,allowoptimization public interface com.chat.effect.IEffectPlayer { public *; }
-keep,allowshrinking,allowoptimization public interface com.chat.effect.IEffectPlayerFactory { public *; }
-keep,allowshrinking,allowoptimization public interface com.chat.effect.PlayCallback { public *; }
-keep,allowshrinking,allowoptimization public interface com.chat.effect.EffectPlaybackListener { public *; }
