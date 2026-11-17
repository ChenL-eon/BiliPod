package com.bilipod.app.common.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 全局主题：安静阅读向（参考小宇宙播客）。
 *
 * 设计原则：
 * 1. 不使用 dynamicColor 动态取色，全部走固定色值，保证任何设备上观感一致；
 * 2. 主色为 BiliPod 蓝（取自应用图标），只用于强调（播放进度、选中态、关键按钮），不做大面积铺色；
 * 3. 背景与卡片只差一点点明度，层次靠留白和文字层级，不靠阴影；
 * 4. 深色模式用炭灰而不是纯黑，避免 OLED 下的强对比和夜间刺眼。
 *
 * 关于 surfaceTint：Material3 会根据 tonalElevation 给 Surface 叠一层主色染色。
 * 本项目要求"卡片几乎无层次差"，所以把 surfaceTint 设为透明，彻底关闭这层染色，
 * 同时所有卡片统一 tonalElevation = 0.dp。
 */

// ---------------------------------------------------------------------------
// 一、基础色板（集中定义，方便后续微调；ColorScheme 只是把语义映射到这些色）
// ---------------------------------------------------------------------------

// —— 浅色模式 ——

/** 主色：BiliPod 蓝。取自应用图标（#00B1FF → #69D2FF）的中间调，保证白字还能看清 */
private val BrandLight = Color(0xFF00A8EC)

/** 主色上的文字/图标：纯白，蓝底上白字对比度才够 */
private val OnBrandLight = Color(0xFFFFFFFF)

/** 极淡主色容器：选中 chips 底色、播放按钮的浅色底 */
private val BrandContainerLight = Color(0xFFDAF2FE)

/** 极淡主色容器上的文字：深蓝，比主色更深，保证可读 */
private val OnBrandContainerLight = Color(0xFF003A56)

/** 次强调：冷蓝灰，用于极少数次要强调（如"已缓存"状态） */
private val SecondaryLight = Color(0xFF5C7A86)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFE1EDF2)
private val OnSecondaryContainerLight = Color(0xFF1F2F36)

/** 三级强调：更淡的蓝灰，仅用于收藏、睡眠定时这类"状态型"提示 */
private val TertiaryLight = Color(0xFF748C96)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFE6EFF2)
private val OnTertiaryContainerLight = Color(0xFF26343A)

/** 页面底色：极浅冷灰。青绿配米白会发脏，所以整站底色一起去掉暖调 */
private val BackgroundLight = Color(0xFFF6F8F8)

/** 卡片底色：纯白，靠明度差和背景分层 */
private val SurfaceLight = Color(0xFFFFFFFF)

/** 主文字：中性炭灰，不用纯黑 */
private val OnSurfaceLight = Color(0xFF1B1F1F)

/** 骨架屏/占位块/进度轨道的底色 */
private val SurfaceVariantLight = Color(0xFFEFF3F2)

/** 次要文字：UP 主名、时间等，再配合 0.5~0.7 的 alpha 使用 */
private val OnSurfaceVariantLight = Color(0xFF74807E)

/** 必要的描边：输入框未聚焦边框、极少数需要边界的容器 */
private val OutlineLight = Color(0xFFBFC9C7)

/** 极浅分割线：只有在必须区分时才用，能不用就不用 */
private val OutlineVariantLight = Color(0xFFE3E9E8)

/** 反色面：Snackbar / Toast 类浮层 */
private val InverseSurfaceLight = Color(0xFF2A2F2E)
private val InverseOnSurfaceLight = Color(0xFFF0F4F3)
private val InversePrimaryLight = Color(0xFF7FD4FF)

/** 错误：低饱和砖红，避免 Material 默认的高饱和红 */
private val ErrorLight = Color(0xFFB3543E)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFF7E4DE)
private val OnErrorContainerLight = Color(0xFF6B2A1C)

// —— 深色模式 ——

/** 主色：同色相更亮的蓝，深色底上不能直接用亮色，会糊 */
private val BrandDark = Color(0xFF5FC8FF)
private val OnBrandDark = Color(0xFF002B3F)
private val BrandContainerDark = Color(0xFF0A3B52)
private val OnBrandContainerDark = Color(0xFFC5E9FA)

private val SecondaryDark = Color(0xFFA8BCC4)
private val OnSecondaryDark = Color(0xFF1A2A30)
private val SecondaryContainerDark = Color(0xFF273A42)
private val OnSecondaryContainerDark = Color(0xFFD3E6EE)

private val TertiaryDark = Color(0xFFA6B8BF)
private val OnTertiaryDark = Color(0xFF1D2A2F)
private val TertiaryContainerDark = Color(0xFF2B3B42)
private val OnTertiaryContainerDark = Color(0xFFD2E2E7)

/** 页面底色：中性深灰，去掉暖调 */
private val BackgroundDark = Color(0xFF131616)

/** 卡片底色：只比背景上浮一点点，避免层次感太强 */
private val SurfaceDark = Color(0xFF1B1F1F)

private val OnSurfaceDark = Color(0xFFE2E7E6)

/** 骨架屏/轨道底色：再深一档，和卡片区分但不跳 */
private val SurfaceVariantDark = Color(0xFF232827)

/** 次要文字：冷灰，避免纯灰发脏 */
private val OnSurfaceVariantDark = Color(0xFF96A09E)

private val OutlineDark = Color(0xFF4C5654)
private val OutlineVariantDark = Color(0xFF2B3231)

private val InverseSurfaceDark = Color(0xFFE2E7E6)
private val InverseOnSurfaceDark = Color(0xFF1B1F1F)
private val InversePrimaryDark = Color(0xFF00A8EC)

private val ErrorDark = Color(0xFFE3A79C)
private val OnErrorDark = Color(0xFF3B1E18)
private val ErrorContainerDark = Color(0xFF4F2A22)
private val OnErrorContainerDark = Color(0xFFF2DAD3)

// ---------------------------------------------------------------------------
// 二、ColorScheme：语义映射
// ---------------------------------------------------------------------------

/**
 * 浅色配色。
 *
 * primary / onPrimary             —— 强调色与其上的文字（进度条进度段、主按钮）
 * primaryContainer / on...Container —— 强调色的极淡底与其上文字（播放钮底、选中标签）
 * secondary                       —— 次强调，暖灰，用量极少
 * tertiary                        —— 状态型提示（收藏、定时），灰绿
 * background / onBackground       —— 页面底色与页面级主文字
 * surface / onSurface             —— 卡片底色与卡片级主文字
 * surfaceVariant / onSurfaceVariant —— 骨架屏、轨道、占位块及其上的次要文字
 * outline / outlineVariant        —— 必要描边 / 极浅分割线
 * inverseSurface / inverseOnSurface / inversePrimary —— 反色浮层
 * error / onError                 —— 错误提示与其上文字
 * errorContainer / onErrorContainer —— 错误提示的淡底与其上文字
 * surfaceTint                     —— 置为透明，关闭 Material 的自动高程染色
 */
internal val LightColors = lightColorScheme(
    primary = BrandLight,
    onPrimary = OnBrandLight,
    primaryContainer = BrandContainerLight,
    onPrimaryContainer = OnBrandContainerLight,

    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,

    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,

    background = BackgroundLight,
    onBackground = OnSurfaceLight,

    surface = SurfaceLight,
    onSurface = OnSurfaceLight,

    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,

    surfaceTint = Color.Transparent,

    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,

    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

/**
 * 深色配色。语义与浅色一致，逐项换成炭灰系色值：
 *
 * primary                     —— 深色下的蓝色强调，亮度够但不刺眼
 * background / surface        —— 中性深灰 #131616 / #1B1F1F，卡片仅上浮一档
 * onBackground / onSurface    —— 冷白文字 #E2E7E6，不用纯白降低眩光
 * onSurfaceVariant            —— 次要文字 #96A09E，保持"淡但不糊"
 * outlineVariant              —— 极浅分割线 #2B3231，深色下几乎看不出来
 */
internal val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = OnBrandDark,
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = OnBrandContainerDark,

    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,

    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,

    background = BackgroundDark,
    onBackground = OnSurfaceDark,

    surface = SurfaceDark,
    onSurface = OnSurfaceDark,

    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,

    surfaceTint = Color.Transparent,

    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

// ---------------------------------------------------------------------------
// 三、Typography：系统默认无衬线，标题加粗放大，说明文字缩小并压暗
// ---------------------------------------------------------------------------

/**
 * 字号体系（单位 sp，行高已按 1.3~1.45 倍估好）：
 *
 * display*        40/36/32 —— 基本用不到，保留 M3 完整性
 * headline*       26/24/22 —— 页面大标题（"BiliPod"、"缓存"、登录页标题）
 * title*          19/17/15 —— 卡片标题、播放器曲名、分区小标题
 * body*           16/14/12 —— 正文、简介、输入框文字
 * label*          14/13/11 —— 按钮文字、页签、UP 名、时间等辅助信息
 *
 * 说明文字的实际"淡"由调用处的 alpha（0.5~0.7）叠加完成，
 * 主题层只负责字号与字重，避免把颜色写死在 TextStyle 里。
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 52.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 48.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 44.sp
    ),

    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),

    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),

    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

// ---------------------------------------------------------------------------
// 四、ShapeScheme：卡片 20、封面 16、按钮输入框 12
// ---------------------------------------------------------------------------

/**
 * extraSmall  8.dp  —— 小标签、极小容器（如播放器里的倍速胶囊内边距容器）
 * small       12.dp —— 按钮、输入框、缩略图内的小块
 * medium      16.dp —— 封面图、图片位、列表里的次级卡片
 * large       20.dp —— 节目卡片（本项目的默认卡片圆角）
 * extraLarge  24.dp —— 弹层、需要更柔的容器
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---------------------------------------------------------------------------
// 五、入口
// ---------------------------------------------------------------------------

/**
 * 全局主题入口。
 *
 * 注意：
 * - 这里刻意不支持 dynamicColor（Android 12+ 壁纸取色），保证一致性；
 * - 深色跟随系统，但可以由外部强制指定（darkTheme 参数）；
 * - 不在这里设置状态栏颜色，交给 res/values{,-night}/themes.xml 控制，
 *   避免冷启动时先白后黑的闪烁。
 *
 * @param darkTheme 是否使用深色配色，默认跟随系统
 */
@Composable
fun BiliPodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
