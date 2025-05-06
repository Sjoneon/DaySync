package com.sjoneon.cap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.TextAppearanceSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.core.content.ContextCompat;

/**
 * 네비게이션 메뉴의 카테고리 스타일을 적용하는 헬퍼 클래스
 * 네비게이션 드로어의 그룹 헤더와 아이템 텍스트 색상을 커스터마이징합니다.
 */
public class NavigationCategoryHelper {

    /**
     * 네비게이션 메뉴의 모든 아이템에 스타일 적용
     * @param context 컨텍스트
     * @param menu 적용할 메뉴
     * @param textColorResId 텍스트 색상 리소스 ID
     */
    public static void applyFontToMenu(Context context, Menu menu, int textColorResId) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem menuItem = menu.getItem(i);

            // 서브메뉴가 있는 경우 (카테고리)
            SubMenu subMenu = menuItem.getSubMenu();
            if (subMenu != null) {
                // 카테고리 헤더 텍스트에 스타일 적용
                SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
                int color = ContextCompat.getColor(context, textColorResId);

                // 텍스트 스타일 설정 (색상, 굵게)
                TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(null,
                        Typeface.BOLD,
                        -1,
                        ColorStateList.valueOf(color),
                        null);

                spanString.setSpan(textAppearanceSpan, 0, spanString.length(), 0);
                menuItem.setTitle(spanString);

                // 서브메뉴 아이템들에도 스타일 적용
                applyFontToMenu(context, subMenu, textColorResId);
            } else {
                // 일반 메뉴 아이템에 스타일 적용
                SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
                int color = ContextCompat.getColor(context, textColorResId);

                TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(null,
                        Typeface.NORMAL,
                        -1,
                        ColorStateList.valueOf(color),
                        null);

                spanString.setSpan(textAppearanceSpan, 0, spanString.length(), 0);
                menuItem.setTitle(spanString);
            }
        }
    }

    /**
     * MainActivity 클래스에서 호출하여 네비게이션 메뉴 스타일 적용
     * @param context 컨텍스트
     * @param navigationView 내비게이션 뷰
     */
    public static void styleNavigationMenu(Context context, com.google.android.material.navigation.NavigationView navigationView) {
        Menu menu = navigationView.getMenu();
        applyFontToMenu(context, menu, R.color.text_primary);

        // 서브헤더 색상 설정 (API 수준에 따라 다른 방식 사용)
        navigationView.setItemTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.text_primary)));
        navigationView.setItemIconTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.text_primary)));
    }
}