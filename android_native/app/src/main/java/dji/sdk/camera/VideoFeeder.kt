package dji.sdk.camera

class VideoFeeder {
    val primaryVideoFeed: VideoFeed? = VideoFeed()

    class VideoFeed {
        fun addVideoDataListener(listener: VideoDataListener) {}
        fun removeVideoDataListener(listener: VideoDataListener) {}
    }

    fun interface VideoDataListener {
        fun onReceive(videoBuffer: ByteArray?, size: Int)
    }

    companion object {
        private val instance = VideoFeeder()
        @JvmStatic
        fun getInstance(): VideoFeeder? = instance
    }
}
