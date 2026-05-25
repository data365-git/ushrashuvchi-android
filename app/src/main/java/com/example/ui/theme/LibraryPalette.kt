package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object LibraryPalette {
    // Backgrounds
    val Background          = Color(0xFFF1F5F9)   // same slate-100 as Recordings tab
    val Surface             = Color(0xFFFFFFFF)
    val CardBg              = Color(0xFFFFFFFF)
    val CardBgPressed       = Color(0xFFF8FAFC)

    // Text
    val OnSurface           = Color(0xFF0F172A)
    val OnSurfaceMuted      = Color(0xFF64748B)
    val OnSurfaceSubtle     = Color(0xFF94A3B8)

    // Borders
    val Outline             = Color(0xFFE2E8F0)
    val OutlineStrong       = Color(0xFFCBD5E1)

    // Primary action (same as Recordings so nav stays consistent)
    val Primary             = Color(0xFF0F172A)
    val OnPrimary           = Color(0xFFFFFFFF)

    // Folder icon / badge
    val FolderIconBg        = Color(0xFFEFF6FF)   // blue-50
    val FolderIconFg        = Color(0xFF3B82F6)   // blue-500

    // Sort chip
    val SortChipBg          = Color(0xFFE2E8F0)
    val SortChipFg          = Color(0xFF0F172A)
    val SortChipActiveBg    = Color(0xFF0F172A)
    val SortChipActiveFg    = Color(0xFFFFFFFF)

    // Multi-select overlay
    val SelectionRing       = Color(0xFF3B82F6)
    val SelectionOverlay    = Color(0x1A3B82F6)   // 10% blue

    // Breadcrumb
    val BreadcrumbBg        = Color(0xFFE2E8F0)
    val BreadcrumbFg        = Color(0xFF475569)
    val BreadcrumbActiveFg  = Color(0xFF0F172A)

    // Snackbar / toast
    val ToastBg             = Color(0xFF1E293B)
    val ToastFg             = Color(0xFFFFFFFF)

    // Empty-state illustration tint
    val EmptyIconTint       = Color(0xFFCBD5E1)

    // FAB (+ New Folder) on Library tab
    val FabBg               = Color(0xFF0F172A)
    val FabFg               = Color(0xFFFFFFFF)
    val FabShadow           = Color(0x660F172A)

    // Recording row inside folder (reuse Recordings tab pills)
    val RecordingRowBg      = Color(0xFFF8FAFC)
    val RecordingRowBgAlt   = Color(0xFFFFFFFF)

    // Count badge on folder card
    val BadgeBg             = Color(0xFFF1F5F9)
    val BadgeFg             = Color(0xFF64748B)
}
