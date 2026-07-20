package com.chat.effect.mp4.alpha

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.chat.effect.EffectLog
import com.chat.effect.EffectResource
import com.chat.effect.IEffectPlayer
import com.chat.effect.PlayCallback
import com.ss.ugc.android.alpha_player.IMonitor
import com.ss.ugc.android.alpha_player.IPlayerAction
import com.ss.ugc.android.alpha_player.controller.PlayerController
import com.ss.ugc.android.alpha_player.model.AlphaVideoViewType
import com.ss.ugc.android.alpha_player.model.Configuration
import com.ss.ugc.android.alpha_player.model.DataSource
import com.ss.ugc.android.alpha_player.model.ScaleType
import java.io.File
import kotlin.math.roundToInt

/**
 * MP4 (alpha 通道) 特效播放器实现，基于字节跳动 AlphaPlayer（[PlayerController]）。
 *
 * AlphaPlayer 的 Android SDK 使用旧版 android.arch.lifecycle，这里内部提供一个轻量
 * lifecycle owner，业务侧不需要把 Activity/Fragment 切到旧 lifecycle 包。
 */
open class AlphaMp4EffectPlayer @JvmOverloads constructor(
    private val alphaVideoViewType: AlphaVideoViewType = AlphaVideoViewType.GL_TEXTURE_VIEW,
    private val portraitScaleType: Int = 1,
    private val landscapeScaleType: Int = 1,
    private val looping: Boolean = false,
) : IEffectPlayer {

    private companion object {
        const val TAG = "AlphaMp4EffectPlayer"
    }

    private var stage: ViewGroup? = null
    private var lifecycleOwner: PlayerLifecycleOwner? = null
    private var controller: PlayerController? = null

    override fun attach(stage: ViewGroup) {
        if (this.stage === stage && controller != null) {
            controller?.setVisibility(View.VISIBLE)
            return
        }
        destroyController()
        this.stage = stage

        val owner = PlayerLifecycleOwner()
        lifecycleOwner = owner
        val configuration = Configuration(stage.context, owner).apply {
            alphaVideoViewType = this@AlphaMp4EffectPlayer.alphaVideoViewType
        }
        controller = PlayerController.get(configuration).apply {
            getView().apply {
                isClickable = false
                isFocusable = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            attachAlphaView(stage)
            setVisibility(View.VISIBLE)
        }
        controller?.let { onPlayerControllerCreated(it) }
    }

    /**
     * 返回当前 AlphaPlayer 控制器。子类可在播放前后读取并设置业务侧需要的 View 属性。
     */
    protected fun getPlayerController(): PlayerController? = controller

    /**
     * AlphaPlayer 控制器创建完成后回调。子类可重写此方法替换监听、设置 View 类型等属性。
     */
    protected open fun onPlayerControllerCreated(controller: PlayerController) = Unit

    /**
     * 每次播放前回调。适合设置和单条素材绑定、或可能被 SDK 播放流程改写的属性。
     */
    protected open fun onBeforeStartPlay(
        controller: PlayerController,
        dataSource: DataSource,
        file: File,
        resource: EffectResource,
    ) = Unit

    override fun play(localPath: String, resource: EffectResource, callback: PlayCallback) {
        val c = controller ?: return callback.onError("alpha player not attached")
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return callback.onError("mp4 file not exists: $localPath")
        }
        val once = OncePlayCallback(callback)
        c.setPlayerAction(object : IPlayerAction {
            override fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int, scaleType: ScaleType) {
                c.getView().post { layoutByVideoSize(c.getView(), videoWidth, videoHeight) }
            }

            override fun startAction() {}

            override fun endAction() {
                once.onComplete()
            }
        })
        c.setMonitor(object : IMonitor {
            override fun monitor(result: Boolean, playType: String, what: Int, extra: Int, errorInfo: String) {
                if (result) return
                EffectLog.e(TAG) {
                    "mp4 play failed url=${resource.url} type=$playType what=$what extra=$extra msg=$errorInfo"
                }
                once.onError("alpha $what:$extra:${errorInfo}")
            }
        })
        c.setVisibility(View.VISIBLE)
        val dataSource = DataSource()
            .setBaseDir(file.parentFile?.absolutePath.orEmpty())
            .setPortraitPath(file.name, portraitScaleType)
            .setLandscapePath(file.name, landscapeScaleType)
            .setLooping(looping)
        onBeforeStartPlay(c, dataSource, file, resource)
        c.start(dataSource)
    }

    private fun layoutByVideoSize(v: View, videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) return

        val parent = (v.parent as? ViewGroup) ?: stage ?: return
        val targetWidth = parent.width.takeIf { it > 0 } ?: parent.resources.displayMetrics.widthPixels
        if (targetWidth <= 0) return

        val targetHeight = (targetWidth * videoHeight.toDouble() / videoWidth.toDouble()).roundToInt()
        val params = when (val current = v.layoutParams) {
            is FrameLayout.LayoutParams -> current
            else -> FrameLayout.LayoutParams(targetWidth, targetHeight)
        }
        params.width = targetWidth
        params.height = targetHeight
        params.gravity = Gravity.CENTER
        v.layoutParams = params
    }

    override fun release() {
        controller?.let { c ->
            c.stop()
            c.reset()
            c.setVisibility(View.GONE)
        }
    }

    private fun destroyController() {
        val oldStage = stage
        controller?.let { c ->
            if (oldStage != null) {
                c.detachAlphaView(oldStage)
            }
            c.release()
        }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        controller = null
        stage = null
    }

    private class PlayerLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override fun getLifecycle(): Lifecycle = registry

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private class OncePlayCallback(
        private val delegate: PlayCallback,
    ) : PlayCallback {
        private var called = false

        override fun onComplete() {
            if (called) return
            called = true
            delegate.onComplete()
        }

        override fun onError(reason: String) {
            if (called) return
            called = true
            delegate.onError(reason)
        }
    }
}
