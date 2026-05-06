package ru.benos.libs.ui_layout.data.theme

data class UiBoxColorTheme(
    var backgroundNormal : UiCanvas,
    var overlayNormal    : List<UiCanvas>? = null,

    var backgroundHovered: UiCanvas  = backgroundNormal,
    var overlayHovered   : List<UiCanvas>? = overlayNormal,

    var backgroundClicked : UiCanvas  = backgroundNormal,
    var overlayClicked    : List<UiCanvas>? = overlayNormal,

    var backgroundReleased: UiCanvas  = backgroundNormal,
    var overlayReleased   : List<UiCanvas>? = overlayNormal,

    var backgroundFocused : UiCanvas  = backgroundNormal,
    var overlayFocused    : List<UiCanvas>? = overlayNormal
) {
    companion object {
        val DEFAULT: UiBoxColorTheme
            get() = UiBoxColorTheme(
                backgroundNormal = UiCanvas.Color(128, 0, 0, 0),
                overlayNormal = listOf(
                    (UiCanvas.Color(128, 255, 255, 255, 1))
                )
            )

        val TRANSPARENT: UiBoxColorTheme
            get() = UiBoxColorTheme(
                backgroundNormal = UiCanvas.Color(0, 0, 0, 0),
                overlayNormal = null
            )
    }

    fun normal(
        background: UiCanvas        = this.backgroundNormal,
        overlays  : List<UiCanvas>? = this.overlayNormal
    ): UiBoxColorTheme {
        backgroundNormal = background
        overlayNormal    = overlays

        return this
    }
    fun normal(
        background: UiCanvas  = this.backgroundNormal,
        overlay   : UiCanvas? = this.overlayNormal?.first()
    ): UiBoxColorTheme =
        normal(background, if (overlay != null) listOf(overlay) else null)

    fun hovered(
        background: UiCanvas        = this.backgroundHovered,
        overlays  : List<UiCanvas>? = this.overlayHovered
    ): UiBoxColorTheme {
        backgroundHovered = background
        overlayHovered    = overlays

        return this
    }
    fun hovered(
        background: UiCanvas  = this.backgroundHovered,
        overlay   : UiCanvas? = this.overlayHovered?.first()
    ): UiBoxColorTheme =
        hovered(background, if (overlay != null) listOf(overlay) else null)

    fun clicked(
        background: UiCanvas        = this.backgroundClicked,
        overlays  : List<UiCanvas>? = this.overlayClicked
    ): UiBoxColorTheme {
        backgroundClicked = background
        overlayClicked    = overlays

        return this
    }
    fun clicked(
        background: UiCanvas  = this.backgroundClicked,
        overlay   : UiCanvas? = this.overlayClicked?.first()
    ): UiBoxColorTheme =
        clicked(background, if (overlay != null) listOf(overlay) else null)

    fun released(
        background: UiCanvas        = this.backgroundReleased,
        overlays  : List<UiCanvas>? = this.overlayReleased
    ): UiBoxColorTheme {
        backgroundReleased = background
        overlayReleased    = overlays

        return this
    }
    fun released(
        background: UiCanvas  = this.backgroundReleased,
        overlay   : UiCanvas? = this.overlayReleased?.first()
    ): UiBoxColorTheme =
        released(background, if (overlay != null) listOf(overlay) else null)

    fun focused(
        background: UiCanvas        = this.backgroundFocused,
        overlays  : List<UiCanvas>? = this.overlayFocused
    ): UiBoxColorTheme {
        backgroundFocused = background
        overlayFocused    = overlays

        return this
    }
    fun focused(
        background: UiCanvas  = this.backgroundFocused,
        overlay   : UiCanvas? = this.overlayFocused?.first()
    ): UiBoxColorTheme =
        focused(background, if (overlay != null) listOf(overlay) else null)
}
