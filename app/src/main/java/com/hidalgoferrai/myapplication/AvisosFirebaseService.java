package com.hidalgoferrai.myapplication;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Solo presenta mensajes de tipo conocido, destinados a la cuenta abierta en este móvil. */
public final class AvisosFirebaseService extends FirebaseMessagingService {
    private static final String CANAL = "casos_y_plazos";

    @Override public void onNewToken(String token) {
        AvisosAndroid.tokenRenovado(this, token);
    }

    @Override public void onMessageReceived(RemoteMessage mensaje) {
        if (!AvisosAndroid.habilitado(this)) return;
        String usuario = Perfil.usuarioId(this);
        if (usuario == null || !usuario.equals(mensaje.getData().get("user_id"))) return;
        int contenido = textoParaTipo(mensaje.getData().get("tipo"));
        if (contenido == 0) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager administrador = getSystemService(NotificationManager.class);
        if (administrador == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL,
                    getString(R.string.avisos_canal), NotificationManager.IMPORTANCE_DEFAULT);
            canal.setDescription(getString(R.string.avisos_canal_detalle));
            canal.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            administrador.createNotificationChannel(canal);
        }
        Intent abrir = new Intent(this, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent destino = PendingIntent.getActivity(this, 0, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder aviso = new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(contenido))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(destino);
        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), aviso.build());
    }

    static int textoParaTipo(String tipo) {
        if ("caso_nuevo".equals(tipo)) return R.string.aviso_caso_nuevo;
        if ("plazo_descargo".equals(tipo)) return R.string.aviso_plazo_descargo;
        if ("pasos_pendientes".equals(tipo)) return R.string.aviso_pasos_pendientes;
        if ("documento_recibido".equals(tipo)) return R.string.aviso_documento_recibido;
        return 0;
    }
}
