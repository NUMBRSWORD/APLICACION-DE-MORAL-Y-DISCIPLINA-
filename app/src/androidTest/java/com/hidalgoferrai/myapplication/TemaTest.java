package com.hidalgoferrai.myapplication;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class TemaTest {
    @Test public void cambioDesdeAccesoSeConservaAlReabrir() {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(contexto.getPackageName().endsWith(".qa"));
        int anterior = AppCompatDelegate.getDefaultNightMode();
        try {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            try (ActivityScenario<LoginActivity> escenario = ActivityScenario.launch(LoginActivity.class)) {
                onView(withId(R.id.btnTema)).perform(click());
                assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
                escenario.recreate();
                escenario.onActivity(a -> assertTrue(Tema.esOscuro(a)));
                onView(withId(R.id.btnTema)).perform(click());
                assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
            }
        } finally {
            contexto.getSharedPreferences("apariencia", Context.MODE_PRIVATE).edit().clear().commit();
            AppCompatDelegate.setDefaultNightMode(anterior);
        }
    }
}
