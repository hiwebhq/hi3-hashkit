package hi3.hashkit.wear

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

private const val RES_VERSION = "1"
private const val BRAND_BLUE = 0xFF3987E5.toInt()
private const val ONLINE_GREEN = 0xFF2BD97C.toInt()
private const val TEXT_DIM = 0xFF93A3B4.toInt()

/** A glanceable Wear OS tile showing the fleet total hashrate and online count. */
class FleetTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val summary = FleetStore.load(this)
        val root = layout(summary, requestParams.deviceConfiguration)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder().setRoot(root).build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
        return ResolvableFuture.create<ResourceBuilders.Resources>().apply { set(resources) }
    }

    private fun layout(
        summary: FleetSummary,
        device: DeviceParameters,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (summary.isEmpty) {
            column.addContent(
                Text.Builder(this, "Hi3 Hashkit")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(BRAND_BLUE))
                    .build(),
            )
            column.addContent(
                Text.Builder(this, "Open the phone app")
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(TEXT_DIM))
                    .setMaxLines(2)
                    .build(),
            )
        } else {
            column.addContent(
                Text.Builder(this, WearFormat.hashrate(summary.totalHashrateGhs))
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                    .setColor(argb(BRAND_BLUE))
                    .build(),
            )
            column.addContent(
                Text.Builder(this, "${summary.online}/${summary.total} online")
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setColor(argb(ONLINE_GREEN))
                    .build(),
            )
            val subtitle = buildString {
                if (summary.offline > 0) append("${summary.offline} offline")
                summary.worstTempC?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append("${it.toInt()}°C")
                }
            }
            if (subtitle.isNotEmpty()) {
                column.addContent(
                    Text.Builder(this, subtitle)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(TEXT_DIM))
                        .build(),
                )
            }
        }

        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setContent(column.build())
            .build()
    }
}
