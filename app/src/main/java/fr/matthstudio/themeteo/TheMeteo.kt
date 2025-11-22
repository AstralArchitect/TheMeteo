package fr.matthstudio.themeteo
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder

class TheMeteo : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory()) // Ajoute le décodeur pour les SVGs
            }
            .build()
    }
}
    