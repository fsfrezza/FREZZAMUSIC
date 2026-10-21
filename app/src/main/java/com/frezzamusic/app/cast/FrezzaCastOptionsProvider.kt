package com.frezzamusic.app.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.frezzamusic.app.R

/** Default Media Receiver: audio and metadata only. Custom lyrics require a registered receiver. */
class FrezzaCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(context.getString(R.string.cast_receiver_app_id))
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
