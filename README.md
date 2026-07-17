# effect

Android 特效播放框架，核心模块负责队列调度、全屏 stage、资源下载与缓存；SVGA、alpha-MP4、GIF 播放能力拆成独立实现模块，业务按需引入。

## 模块

| 模块 | 作用 | 是否包含播放器实现 |
| --- | --- | --- |
| `effect-core` | 核心队列、stage、下载、缓存、播放调度 | 否 |
| `effect-svga` | SVGA 播放实现，基于 `SVGAPlayer-Android` | 是 |
| `effect-mp4-alpha` | alpha-MP4 播放实现，基于字节跳动 AlphaPlayer | 是 |
| `effect-mp4-vap` | alpha-MP4/VAP 播放实现，基于腾讯 VAP | 是 |
| `effect-mp4-yyeva` | alpha-MP4 播放实现，基于 YYEVA | 是 |
| `effect-gif-glide` | GIF 播放实现，基于 Glide | 是 |

只引入 `effect-core` 只能使用核心调度能力，不会带入任何具体格式的播放实现。需要哪种格式，就额外引入对应模块；未引入的格式不会有播放能力，也不会带入对应三方依赖。

## 引入依赖

### 1. 添加 JitPack 仓库

在 `settings.gradle.kts` 中添加：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 按需添加模块

当前发布配置使用的 `groupId` 是 `com.github.gegeName`，版本使用 Git tag 或 JitPack 版本号。

```kotlin
dependencies {
    // 核心能力：队列、stage、下载、缓存
    implementation("com.github.gegeName:effect-core:0.0.4")

    // 需要 SVGA 时引入
    implementation("com.github.gegeName:effect-svga:0.0.4")

    // 需要 alpha-MP4/VAP 时引入
    implementation("com.github.gegeName:effect-mp4-vap:0.0.4")

    // 需要 alpha-MP4/YYEVA 时引入
    implementation("com.github.gegeName:effect-mp4-yyeva:0.0.4")

    // 需要 alpha-MP4/AlphaPlayer 时引入
    implementation("com.github.gegeName:effect-mp4-alpha:0.0.4")

    // 需要 GIF/Glide 时引入
    implementation("com.github.gegeName:effect-gif-glide:0.0.4")
}
```

实现模块会传递依赖 `effect-core`。例如业务只需要 SVGA，可以只引入：

```kotlin
implementation("com.github.gegeName:effect-svga:0.0.4")
```

## 快速开始

### 1. 创建播放器工厂

业务侧负责把资源类型映射到具体播放器。引入了哪个实现模块，就注册哪个播放器。

```kotlin
import com.chat.effect.EffectChannel
import com.chat.effect.EffectType
import com.chat.effect.IEffectPlayer
import com.chat.effect.IEffectPlayerFactory
import com.chat.effect.gif.glide.GifEffectPlayer
import com.chat.effect.mp4.vap.VapMp4EffectPlayer
// import com.chat.effect.mp4.yyeva.YyEvaMp4EffectPlayer
// import com.chat.effect.mp4.alpha.AlphaMp4EffectPlayer
import com.chat.effect.svga.SvgaEffectPlayer
import java.util.concurrent.ConcurrentHashMap

class AppEffectPlayerFactory : IEffectPlayerFactory {
    private val cache = ConcurrentHashMap<Pair<EffectType, EffectChannel>, IEffectPlayer>()

    override fun create(type: EffectType, channel: EffectChannel): IEffectPlayer =
        cache.getOrPut(type to channel) {
            when (type) {
                EffectType.SVGA -> SvgaEffectPlayer()
                EffectType.MP4 -> VapMp4EffectPlayer()
                // EffectType.MP4 -> YyEvaMp4EffectPlayer()
                // EffectType.MP4 -> AlphaMp4EffectPlayer()
                EffectType.GIF -> GifEffectPlayer()
                else -> error("no player registered for type=${type.key}")
            }
        }
}
```

同一个 `EffectType.MP4` 只能注册一个 MP4 播放器实现。VAP、YYEVA、AlphaPlayer 三个模块可以同时存在于仓库里，但业务工厂里按实际素材格式选择其中一个返回。

多通道并发播放时，同类型不同 `channel` 不能共用同一个播放器实例，所以建议按 `(type, channel)` 缓存。

### 2. 初始化

在 `Application.onCreate` 中初始化：

```kotlin
import android.app.Application
import com.chat.effect.EffectManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        EffectManager.init(this, AppEffectPlayerFactory())
        EffectManager.enableAutoStage(this)
    }
}
```

`enableAutoStage` 会在当前 Activity 的 `android.R.id.content` 上自动添加全屏 `EffectStageView`，业务布局无需手动放 stage。

如果只希望部分 Activity 支持特效：

```kotlin
EffectManager.enableAutoStage(this) { activity ->
    activity !is SplashActivity
}
```

### 3. 播放特效

URL 后缀会自动推断类型：`.svga`、`.mp4`、`.gif`。

```kotlin
import com.chat.effect.EffectManager
import com.chat.effect.EffectPriority

EffectManager.enqueue("https://example.com/gift.svga")

EffectManager.enqueue(
    url = "https://example.com/vip-entry.mp4",
    priority = EffectPriority.HIGH,
    tag = "vip-entry",
    persistent = true,
)

EffectManager.enqueue("https://example.com/gift.gif")
```

`HIGH` 会插到队首，但不会打断当前正在播放的特效。`persistent = true` 表示 stage 切换或 Activity 切换时不丢弃，下次有 stage 时继续播放；只有 `EffectManager.clear()` 会强制清掉。

## 手动 Stage

如果不使用自动 stage，可以在布局中放置 `EffectStageView`：

```xml
<com.chat.effect.EffectStageView
    android:id="@+id/effectStage"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

然后在页面生命周期中绑定和解绑：

```kotlin
import com.chat.effect.EffectManager

override fun onResume() {
    super.onResume()
    EffectManager.attach(binding.effectStage)
}

override fun onPause() {
    EffectManager.detach(binding.effectStage)
    super.onPause()
}
```

`EffectStageView` 不拦截触摸事件，覆盖在业务 UI 上方时不会影响底层点击。

## 多通道并发

默认不传 `channel` 时走 `EffectChannel.DEFAULT`，同一通道内串行播放。自定义通道可以让不同类型业务同屏并发播放：

```kotlin
import com.chat.effect.EffectChannel
import com.chat.effect.EffectManager
import com.chat.effect.EffectPriority

object VipChannel : EffectChannel("vip")
object GiftChannel : EffectChannel("gift")

EffectManager.enqueue(
    url = "https://example.com/vip-entry.svga",
    channel = VipChannel,
    priority = EffectPriority.HIGH,
)

EffectManager.enqueue(
    url = "https://example.com/gift.mp4",
    channel = GiftChannel,
)
```

上面两条资源会在同一个 stage 上并发播放；同一个 channel 内仍然按队列串行。

## 预下载

```kotlin
EffectManager.preload("https://example.com/gift.svga")
EffectManager.preload(
    listOf(
        "https://example.com/gift.svga",
        "https://example.com/vip-entry.mp4",
    )
)
```

如果项目已有统一的 `OkHttpClient`，可以注入给下载模块复用连接池、鉴权拦截器和超时配置：

```kotlin
import com.chat.effect.DownloadClient

DownloadClient.setSharedClient(okHttpClient)
```

## 播放监听

```kotlin
import com.chat.effect.EffectPlaybackListener
import com.chat.effect.EffectResource

val listener = object : EffectPlaybackListener {
    override fun onStart(resource: EffectResource) {
        // 已下载完成，播放器开始播放
    }

    override fun onComplete(resource: EffectResource) {
        // 单条资源播放完成
    }

    override fun onError(resource: EffectResource, reason: String) {
        // 下载或播放失败
    }
}

EffectManager.addPlaybackListener(listener)
EffectManager.removePlaybackListener(listener)
```

所有监听回调都在 Main 线程。

## 清空队列

```kotlin
// 清空所有通道，包括 persistent 资源
EffectManager.clear()

// 只清空指定通道
EffectManager.clear(VipChannel)
```

## 自定义格式

业务可以扩展 `EffectType` 和 `IEffectPlayer`，接入 WebP、APNG、自研动画或纯代码 View 动画。

```kotlin
import com.chat.effect.EffectType

object WebpEffectType : EffectType(
    key = "webp",
    urlSuffix = "webp",
)

// Application.onCreate
EffectType.register(WebpEffectType)
```

然后在 `IEffectPlayerFactory.create` 中返回自己的播放器：

```kotlin
when (type) {
    WebpEffectType -> WebpEffectPlayer()
    else -> error("no player registered for type=${type.key}")
}
```

对于不需要下载远端资源的纯代码动画，可以声明 `needsDownload = false`，并通过 `extras` 传参：

```kotlin
object PopupTextEffectType : EffectType(
    key = "popup_text",
    urlSuffix = null,
    needsDownload = false,
)

EffectManager.enqueue(
    EffectResource(
        url = "",
        type = PopupTextEffectType,
        extras = mapOf("text" to "升级!"),
    )
)
```

`needsDownload = false` 时框架会跳过下载，直接调用 `player.play("", resource, callback)`。

## 混淆

各模块已经通过 `consumer-rules.pro` 暴露消费者混淆规则。业务侧正常引入 AAR 即可；如果自定义播放器依赖的三方库有额外规则，请按对应三方库文档补充。

## 许可证

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源。Apache 2.0 自带"按现状提供、不作担保、不承担责任"的条款，并额外包含专利授权与责任限制条款。

## 免责声明 / Disclaimer

本项目（以下简称"本软件"）是一个通用的媒体选择工具，仅供学习、研究和合法用途使用。

1. 本软件按"现状"提供，作者不对其适用性、可靠性、安全性作任何明示或暗示的担保。
2. 使用者应自行遵守所在国家/地区的法律法规。对于使用者利用本软件从事的任何违法、侵权或其他不当行为，作者不承担由此产生的任何责任。
3. 本软件不针对任何违法用途设计，作者不认可、不支持将其用于任何违反法律法规的用途。
4. 在适用法律允许的最大范围内，作者不对因使用或无法使用本软件而导致的任何直接或间接损失承担责任。
5. 使用本软件即表示使用者已知悉并接受以上条款。
