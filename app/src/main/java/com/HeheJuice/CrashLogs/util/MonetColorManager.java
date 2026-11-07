package com.HeheJuice.CrashLogs.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.ColorResourcesOverride;
import com.google.android.material.color.DynamicColors;
import com.materialkolor.dynamiccolor.ColorSpec;
import com.materialkolor.hct.Hct;
import com.materialkolor.scheme.DynamicScheme;
import com.materialkolor.scheme.SchemeTonalSpot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies Material 3 dynamic color (always Spec 2025) to every activity.
 *
 * ★ Seed color 唔 cache，確保 wallpaper 變更後取到新色。
 */
public final class MonetColorManager {

    private static final int FALLBACK_SEED_COLOR = Color.rgb(103, 80, 164);

    /** 顏色 cache（key = "type|seed|dark|contrast"）。LRU，上限 128。 */
    private static final Map<String, Integer> COLOR_CACHE =
            new LinkedHashMap<String, Integer>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 128;
                }
            };

    private MonetColorManager() {}

    @SuppressLint("RestrictedApi")
    public static void applyToActivity(Activity activity) {
        ColorResourcesOverride resourcesOverride = ColorResourcesOverride.getInstance();
        if (resourcesOverride == null) {
            DynamicColors.applyToActivityIfAvailable(activity);
            return;
        }

        SchemeTonalSpot scheme = buildScheme(activity, isDarkTheme(activity));

        if (!resourcesOverride.applyIfPossible(activity, createMaterialColorMap(scheme))) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Cached getters（key 包含 seed，wallpaper 變 → key 變 → 自動用新色）
    // ─────────────────────────────────────────────────────────────────

    public static int getSurfaceContainerColor(Context context, boolean dark) {
        int seed = resolveSystemSeedColor(context);
        double contrast = resolveSystemContrast(context);
        String key = "surf|" + seed + "|" + dark + "|" + contrast;

        synchronized (COLOR_CACHE) {
            Integer cached = COLOR_CACHE.get(key);
            if (cached != null) return cached;

            int v;
            try {
                v = buildSchemeWithSeed(seed, contrast, dark).getSurfaceContainer();
            } catch (Throwable t) {
                v = dark ? 0xFF1C1B1F : 0xFFFEF7FF;
            }
            COLOR_CACHE.put(key, v);
            return v;
        }
    }

    public static int getPrimaryContainerColor(Context context, boolean dark) {
        int seed = resolveSystemSeedColor(context);
        double contrast = resolveSystemContrast(context);
        String key = "pc|" + seed + "|" + dark + "|" + contrast;

        synchronized (COLOR_CACHE) {
            Integer cached = COLOR_CACHE.get(key);
            if (cached != null) return cached;

            int v;
            try {
                v = buildSchemeWithSeed(seed, contrast, dark).getPrimaryContainer();
            } catch (Throwable t) {
                v = dark ? 0xFF4F378B : 0xFFEADDFF;
            }
            COLOR_CACHE.put(key, v);
            return v;
        }
    }

    public static int getOnPrimaryContainerColor(Context context, boolean dark) {
        int seed = resolveSystemSeedColor(context);
        double contrast = resolveSystemContrast(context);
        String key = "opc|" + seed + "|" + dark + "|" + contrast;

        synchronized (COLOR_CACHE) {
            Integer cached = COLOR_CACHE.get(key);
            if (cached != null) return cached;

            int v;
            try {
                v = buildSchemeWithSeed(seed, contrast, dark).getOnPrimaryContainer();
            } catch (Throwable t) {
                v = dark ? 0xFFEADDFF : 0xFF21005D;
            }
            COLOR_CACHE.put(key, v);
            return v;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Palette 變化偵測用
    // ─────────────────────────────────────────────────────────────────

    /**
     * 讀取當前系統 seed color（不經 cache）。
     *
     * ★ 供 {@code EngineStart} 偵測 Monet palette 是否已變更使用。
     *   由於 cache 的 key 已包含 seed，正常讀色流程不需呼叫此方法；
     *   此方法純粹用於「比較新舊 seed 是否不同」的情境。
     *
     * @return 當前系統 seed color，若無法讀取則回傳 {@link #FALLBACK_SEED_COLOR}。
     */
    public static int getCurrentSeedColor(Context context) {
        return resolveSystemSeedColor(context);
    }

    /** 清空 cache（wallpaper 變更時可選 call；一般唔需要）。 */
    public static void clearCache() {
        synchronized (COLOR_CACHE) {
            COLOR_CACHE.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────

    private static SchemeTonalSpot buildScheme(Context context, boolean dark) {
        return buildSchemeWithSeed(
                resolveSystemSeedColor(context),
                resolveSystemContrast(context),
                dark
        );
    }

    private static SchemeTonalSpot buildSchemeWithSeed(
            int seed, double contrast, boolean dark
    ) {
        return new SchemeTonalSpot(
                Hct.Companion.fromInt(seed),
                dark,
                contrast,
                ColorSpec.SpecVersion.SPEC_2025,
                DynamicScheme.Platform.PHONE
        );
    }

    private static int resolveSystemSeedColor(Context context) {
        try {
            return ContextCompat.getColor(context, android.R.color.system_accent1_500);
        } catch (RuntimeException ignored) {
            return FALLBACK_SEED_COLOR;
        }
    }

    private static double resolveSystemContrast(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return 0.0;
        }
        UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
        return uiModeManager == null ? 0.0 : uiModeManager.getContrast();
    }

    private static boolean isDarkTheme(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressLint("PrivateResource")
    private static Map<Integer, Integer> createMaterialColorMap(DynamicScheme scheme) {
        Map<Integer, Integer> colors = new HashMap<>();
        colors.put(com.google.android.material.R.color.material_personalized_color_primary, scheme.getPrimary());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_primary, scheme.getOnPrimary());
        colors.put(com.google.android.material.R.color.material_personalized_color_primary_inverse, scheme.getInversePrimary());
        colors.put(com.google.android.material.R.color.material_personalized_color_primary_container, scheme.getPrimaryContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_primary_container, scheme.getOnPrimaryContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_secondary, scheme.getSecondary());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_secondary, scheme.getOnSecondary());
        colors.put(com.google.android.material.R.color.material_personalized_color_secondary_container, scheme.getSecondaryContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_secondary_container, scheme.getOnSecondaryContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_tertiary, scheme.getTertiary());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_tertiary, scheme.getOnTertiary());
        colors.put(com.google.android.material.R.color.material_personalized_color_tertiary_container, scheme.getTertiaryContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_tertiary_container, scheme.getOnTertiaryContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_background, scheme.getBackground());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_background, scheme.getOnBackground());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface, scheme.getSurface());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_surface, scheme.getOnSurface());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_variant, scheme.getSurfaceVariant());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_surface_variant, scheme.getOnSurfaceVariant());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_inverse, scheme.getInverseSurface());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_surface_inverse, scheme.getInverseOnSurface());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_bright, scheme.getSurfaceBright());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_dim, scheme.getSurfaceDim());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_container, scheme.getSurfaceContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_container_low, scheme.getSurfaceContainerLow());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_container_high, scheme.getSurfaceContainerHigh());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_container_lowest, scheme.getSurfaceContainerLowest());
        colors.put(com.google.android.material.R.color.material_personalized_color_surface_container_highest, scheme.getSurfaceContainerHighest());
        colors.put(com.google.android.material.R.color.material_personalized_color_outline, scheme.getOutline());
        colors.put(com.google.android.material.R.color.material_personalized_color_outline_variant, scheme.getOutlineVariant());
        colors.put(com.google.android.material.R.color.material_personalized_color_error, scheme.getError());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_error, scheme.getOnError());
        colors.put(com.google.android.material.R.color.material_personalized_color_error_container, scheme.getErrorContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_on_error_container, scheme.getOnErrorContainer());
        colors.put(com.google.android.material.R.color.material_personalized_color_control_activated, scheme.getControlActivated());
        colors.put(com.google.android.material.R.color.material_personalized_color_control_normal, scheme.getControlNormal());
        colors.put(com.google.android.material.R.color.material_personalized_color_control_highlight, scheme.getControlHighlight());
        colors.put(com.google.android.material.R.color.material_personalized_color_text_primary_inverse, scheme.getTextPrimaryInverse());
        colors.put(com.google.android.material.R.color.material_personalized_color_text_secondary_and_tertiary_inverse, scheme.getTextSecondaryAndTertiaryInverse());
        colors.put(com.google.android.material.R.color.material_personalized_color_text_secondary_and_tertiary_inverse_disabled, scheme.getTextSecondaryAndTertiaryInverseDisabled());
        colors.put(com.google.android.material.R.color.material_personalized_color_text_primary_inverse_disable_only, scheme.getTextPrimaryInverseDisableOnly());
        colors.put(com.google.android.material.R.color.material_personalized_color_text_hint_foreground_inverse, scheme.getTextHintInverse());
        return colors;
    }
}