package com.hidalgoferrai.myapplication;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Comprueba que la configuración de Firebase del proyecto sirve de verdad: que el
 * teléfono consigue un token de dispositivo y que ese token cumple lo que exige el
 * servidor al registrarlo (entre 32 y 4096 caracteres y sin espacios).
 *
 * Se omite sola en las copias del proyecto que no tienen `app/google-services.json`,
 * que está fuera de Git: allí la aplicación compila pero no registra ni recibe avisos.
 */
@RunWith(AndroidJUnit4.class)
public class AvisosTest {

    @Test public void firebaseEntregaUnTokenQueElServidorAceptaria() throws Exception {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Esta copia no tiene google-services.json", AvisosAndroid.configurado(contexto));

        String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 60, TimeUnit.SECONDS);

        assertNotNull("Firebase no entregó token de dispositivo", token);
        assertTrue("El servidor solo acepta tokens de 32 a 4096 caracteres: " + token.length(),
                token.length() >= 32 && token.length() <= 4096);
        assertFalse("El servidor rechaza tokens con espacios", token.matches("(?s).*\\s.*"));
    }
}
