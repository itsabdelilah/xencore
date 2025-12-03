package io.ads.mediation

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Flexible native ad binder that works with any app's custom layout.
 *
 * This allows each app to provide their own layout XML with unique styling
 * (colors, fonts, shapes) while this binder handles the asset population logic.
 *
 * ## Required View IDs in App's Layout:
 * - **R.id.ad_headline** (TextView) - REQUIRED
 * - **R.id.ad_body** (TextView) - REQUIRED
 * - **R.id.ad_call_to_action** (Button) - REQUIRED
 *
 * ## Optional View IDs:
 * - R.id.ad_app_icon (ImageView) - App icon
 * - R.id.ad_stars (RatingBar) - Star rating
 * - R.id.ad_advertiser (TextView) - Advertiser name
 * - R.id.ad_media_view (MediaView) - Media content (images/video)
 *
 * ## Usage Example:
 * ```kotlin
 * val adView = layoutInflater.inflate(R.layout.my_custom_native, container, false) as NativeAdView
 * NativeAdBinder.bind(nativeAd, adView, hideEmptyFields = true)
 * container.addView(adView)
 * ```
 *
 * @see AdsManager.loadNative
 */
object NativeAdBinder {

    /**
     * Bind native ad assets to a custom layout.
     *
     * This method:
     * 1. Assigns view references to the NativeAdView
     * 2. Populates each view with ad data
     * 3. Handles missing optional fields gracefully
     * 4. Registers the ad with the NativeAdView
     *
     * @param nativeAd The loaded native ad containing all assets
     * @param adView The NativeAdView (root of the layout from app)
     * @param hideEmptyFields If true, hides optional fields when data is missing (GONE).
     *                        If false, makes them invisible (INVISIBLE) to preserve layout.
     */
    fun bind(
        nativeAd: NativeAd,
        adView: NativeAdView,
        hideEmptyFields: Boolean = true
    ) {
        // ========== ASSIGN VIEW REFERENCES ==========

        // Helper to find views by resource name (works across app modules)
        val resources = adView.context.resources
        val packageName = adView.context.packageName

        fun getIdByName(name: String): Int {
            return resources.getIdentifier(name, "id", packageName)
        }

        // Required fields
        adView.headlineView = adView.findViewById(getIdByName("ad_headline"))
        adView.bodyView = adView.findViewById(getIdByName("ad_body"))
        adView.callToActionView = adView.findViewById(getIdByName("ad_call_to_action"))

        // Optional fields
        adView.iconView = adView.findViewById(getIdByName("ad_app_icon"))
        adView.starRatingView = adView.findViewById(getIdByName("ad_stars"))
        adView.advertiserView = adView.findViewById(getIdByName("ad_advertiser"))
        adView.mediaView = adView.findViewById(getIdByName("ad_media_view"))

        // ========== POPULATE REQUIRED FIELDS ==========

        // Headline (required)
        (adView.headlineView as? TextView)?.text = nativeAd.headline ?: ""

        // Body (required)
        (adView.bodyView as? TextView)?.text = nativeAd.body ?: ""

        // Call to Action button (required)
        (adView.callToActionView as? Button)?.text = nativeAd.callToAction ?: "Install"

        // ========== POPULATE OPTIONAL FIELDS ==========

        // App Icon
        if (nativeAd.icon != null) {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } else {
            adView.iconView?.visibility = if (hideEmptyFields) View.GONE else View.INVISIBLE
        }

        // Star Rating
        if (nativeAd.starRating != null && nativeAd.starRating!! > 0) {
            (adView.starRatingView as? RatingBar)?.rating = nativeAd.starRating!!.toFloat()
            adView.starRatingView?.visibility = View.VISIBLE
        } else {
            adView.starRatingView?.visibility = if (hideEmptyFields) View.GONE else View.INVISIBLE
        }

        // Advertiser
        if (!nativeAd.advertiser.isNullOrBlank()) {
            (adView.advertiserView as? TextView)?.text = nativeAd.advertiser
            adView.advertiserView?.visibility = View.VISIBLE
        } else {
            adView.advertiserView?.visibility = if (hideEmptyFields) View.GONE else View.INVISIBLE
        }

        // Media View (images/video)
        nativeAd.mediaContent?.let { mediaContent ->
            (adView.mediaView as? MediaView)?.let { mediaView ->
                mediaView.mediaContent = mediaContent
                mediaView.visibility = View.VISIBLE
            }
        } ?: run {
            adView.mediaView?.visibility = if (hideEmptyFields) View.GONE else View.INVISIBLE
        }

        // ========== REGISTER AD ==========

        // This tells the SDK which views are clickable and tracks impressions
        adView.setNativeAd(nativeAd)
    }
}
