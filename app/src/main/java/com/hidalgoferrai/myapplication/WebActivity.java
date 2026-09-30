package com.hidalgoferrai.myapplication;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.ScriptHandler;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.ServiceWorkerClientCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebActivity extends AppCompatActivity {

    private static final String URL_APP = "https://numbrsword.github.io/moral-y-disciplina/";
    private static final String HOST_APP = "numbrsword.github.io";
    private static final String ORIGEN_APP = "https://numbrsword.github.io";
    private static final int MAX_BASE64 = 28_000_000;

    // La app web guarda sus documentos con file-saver (enlaces blob:). El WebView no los descarga
    // solo, así que se leen desde la página y se entregan a PuenteDescargas.
    private static final String JS_PUENTE = "(function(){"
            + "if(window.__puenteAndroid)return;window.__puenteAndroid=true;"
            + "window.__guardarBlob=function(href,nombre){"
            + "fetch(href).then(function(r){return r.blob();}).then(function(b){"
            + "var f=new FileReader();"
            + "f.onloadend=function(){var d=String(f.result);"
            + "AndroidDescargas.postMessage(JSON.stringify({base64:d.slice(d.indexOf(',')+1),"
            + "tipo:b.type||'application/octet-stream',nombre:nombre||'documento'}));};"
            + "f.readAsDataURL(b);});};"
            + "function esBlob(a){return String(a.href).indexOf('blob:')===0&&a.download!=='';}"
            + "var clic=HTMLAnchorElement.prototype.click;"
            + "HTMLAnchorElement.prototype.click=function(){"
            + "if(esBlob(this)){window.__guardarBlob(this.href,this.download);return;}"
            + "clic.call(this);};"
            + "var despachar=HTMLAnchorElement.prototype.dispatchEvent;"
            + "HTMLAnchorElement.prototype.dispatchEvent=function(e){"
            + "if(e&&e.type==='click'&&esBlob(this)){"
            + "window.__guardarBlob(this.href,this.download);return true;}"
            + "return despachar.call(this,e);};"
            + "})();";

    public static final String EXTRA_SESION = "sesion";
    public static final String EXTRA_VISTA = "vista";
    public static final String EXTRA_DOCUMENTO = "documento";
    public static final String EXTRA_NOMBRE_DOCUMENTO = "nombre_documento";
    public static final String EXTRA_NOTA_ID = "nota_id";
    // Fuentes aisladas para pruebas: nunca se consultan en la aplicación de uso real.
    static volatile String htmlPrueba, moduloPrueba;

    private WebView webView;
    private LinearProgressIndicator progreso;
    private ValueCallback<Uri[]> callbackArchivos;
    private String sesionParaWeb;
    private boolean puenteDisponible;
    private final ExecutorService descargas = Executors.newSingleThreadExecutor();
    private final ExecutorService sesionHilo = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String nonce = UUID.randomUUID().toString();
    private String seccion, documentoUrl;
    private Uri documento;
    private ScriptHandler scriptInicio;
    private JSONObject configuracionWeb;
    private String scriptTema;
    private static Object receptorSW;
    private final Object propietarioSW = new Object();
    private boolean moduloListo;
    private boolean falloModulo;
    private long epocaSesion;
    private int solicitudCarga;
    private final Runnable tiempoAgotado = () -> mostrarError(R.string.modulo_no_disponible);

    private final ActivityResultLauncher<Intent> selectorArchivos = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (callbackArchivos != null) {
                    callbackArchivos.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(
                            resultado.getResultCode(), resultado.getData()));
                    callbackArchivos = null;
                }
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_web);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        webView = findViewById(R.id.webView);
        progreso = findViewById(R.id.progreso);
        sesionParaWeb = getIntent().getStringExtra(EXTRA_SESION);
        if (sesionParaWeb != null) SesionActual.restaurar(this, sesionParaWeb);
        epocaSesion = SesionActual.epoca();
        seccion = getIntent().getStringExtra(EXTRA_VISTA);
        if ("seguridad".equals(seccion)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (seccion != null && !seccion.matches("cumplimiento|seguimiento|efectivos|recepcion|detalle|registro|consulta|recepcion-fisica|panel|herramientas|roles|directivas|agenda|documentos|historial|reincorporacion|continuan|expedientes-lote|seguridad")) {
            finish(); return;
        }
        String uriDocumento = getIntent().getStringExtra(EXTRA_DOCUMENTO);
        if (uriDocumento != null && ("registro".equals(seccion) || "expedientes-lote".equals(seccion))) {
            Uri uri = Uri.parse(uriDocumento);
            if ("content".equals(uri.getScheme())) {
                documento = uri;
                // Origen local distinto: el service worker de la web no puede cachear el PDF privado.
                documentoUrl = "https://appassets.androidplatform.net/__mobile_document__/" + nonce + ".pdf";
            }
        }
        ((TextView)findViewById(R.id.tvModuloTitulo)).setText(tituloModulo());
        ((TextView)findViewById(R.id.tvCargaModulo)).setText(getString(R.string.modulo_cargando, getString(tituloModulo())));
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> cargarModulo());

        WebSettings ajustes = webView.getSettings();
        ajustes.setJavaScriptEnabled(true);
        ajustes.setDomStorageEnabled(true);
        ajustes.setAllowFileAccess(false);
        ajustes.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ajustes.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(ajustes, false);
        aplicarTemaNativo();

        configurarPuenteSeguro();
        configurarServiceWorker();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView vista, WebResourceRequest solicitud) {
                Uri uri = solicitud.getUrl();
                String esquema = uri.getScheme();
                if (("https".equals(esquema) && HOST_APP.equals(uri.getHost())
                        && uri.getPath() != null && uri.getPath().startsWith("/moral-y-disciplina/"))
                        || "blob".equals(esquema)
                        || "about".equals(esquema)) {
                    return false;
                }
                abrirExternamente(uri);
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView vista, WebResourceRequest solicitud) {
                return recurso(solicitud.getUrl());
            }

            @Override
            public void onPageFinished(WebView vista, String url) {
                String servidor = url == null ? null : Uri.parse(url).getHost();
                if (!HOST_APP.equals(servidor) || !"https".equals(Uri.parse(url).getScheme())) {
                    return;
                }
                if (puenteDisponible) {
                    vista.evaluateJavascript(JS_PUENTE, null);
                }
                if (seccion == null) listo();
            }

            @Override
            public void onReceivedError(WebView vista, WebResourceRequest solicitud,
                                        WebResourceError error) {
                if (solicitud.isForMainFrame()) {
                    mostrarError(R.string.web_error_mensaje);
                }
            }

            @Override public void onReceivedHttpError(WebView vista, WebResourceRequest solicitud, WebResourceResponse error) {
                if (solicitud.isForMainFrame()) mostrarError(R.string.web_error_mensaje);
            }

            @Override
            public boolean onRenderProcessGone(WebView vista, RenderProcessGoneDetail detalle) {
                Toast.makeText(WebActivity.this, R.string.web_error_proceso,
                        Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView vista, int avance) {
                if (!moduloListo && !falloModulo) progreso.setVisibility(View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView vista, ValueCallback<Uri[]> callback,
                                             FileChooserParams parametros) {
                if (callbackArchivos != null) {
                    callbackArchivos.onReceiveValue(null);
                }
                callbackArchivos = callback;
                try {
                    selectorArchivos.launch(parametros.createIntent());
                } catch (ActivityNotFoundException e) {
                    callbackArchivos = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
        webView.setDownloadListener((url, agente, disposicion, tipo, largo) -> {
            if (url.startsWith("blob:")) {
                if (puenteDisponible) {
                    String nombre = URLUtil.guessFileName(url, disposicion, tipo);
                    webView.evaluateJavascript("window.__guardarBlob&&window.__guardarBlob("
                            + JSONObject.quote(url) + "," + JSONObject.quote(nombre) + ");", null);
                } else {
                    avisar(getString(R.string.web_descarga_error));
                }
            } else {
                abrirExternamente(Uri.parse(url));
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (seccion == null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        cargarModulo();
    }

    @Override protected void onResume() {
        super.onResume();
        // Al volver de una herramienta hija se recupera el interceptor de esta pantalla.
        if (webView != null) {
            configurarServiceWorker();
            if (moduloListo) webView.evaluateJavascript("window.dispatchEvent(new Event('faltos-resume'))", null);
        }
    }

    private void configurarServiceWorker() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            receptorSW = propietarioSW;
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(new ServiceWorkerClientCompat() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest solicitud) {
                    return recurso(solicitud.getUrl());
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle salida) {
        super.onSaveInstanceState(salida);
        // Se reconstruye el módulo elegido; nunca se restaura un dashboard obsoleto.
    }

    @Override
    protected void onDestroy() {
        if (callbackArchivos != null) { callbackArchivos.onReceiveValue(null); callbackArchivos = null; }
        descargas.shutdownNow();
        sesionHilo.shutdown();
        handler.removeCallbacksAndMessages(null);
        if (scriptInicio != null) scriptInicio.remove();
        if (receptorSW == propietarioSW) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE))
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(null);
            receptorSW = null;
        }
        webView.destroy();
        super.onDestroy();
    }

    private void abrirExternamente(Uri uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())
                && !"mailto".equals(uri.getScheme()) && !"tel".equals(uri.getScheme())) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.web_sin_aplicacion, Toast.LENGTH_SHORT).show();
        }
    }

    private void avisar(String mensaje) {
        runOnUiThread(() -> Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show());
    }

    private void configurarPuenteSeguro() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return;
        }
        WebViewCompat.addWebMessageListener(webView, "AndroidDescargas",
                Collections.singleton(ORIGEN_APP),
                (vista, mensaje, origen, esMarcoPrincipal, respuesta) -> {
                    if (!esMarcoPrincipal || !"https".equals(origen.getScheme())
                            || !HOST_APP.equals(origen.getHost()) || vista.getUrl() == null
                            || !vista.getUrl().startsWith(URL_APP)) {
                        return;
                    }
                    procesarDescarga(mensaje);
                });
        puenteDisponible = true;
        WebViewCompat.addWebMessageListener(webView, "AndroidNavegacion", Collections.singleton(ORIGEN_APP),
                (vista, mensaje, origen, principal, respuesta) -> {
                    if (!principal || !ORIGEN_APP.equals(origen.toString()) || vista.getUrl() == null
                            || !vista.getUrl().startsWith(URL_APP)) return;
                    try {
                        JSONObject datos = new JSONObject(mensaje.getData());
                        if (!nonce.equals(datos.optString("nonce"))) return;
                        switch (datos.optString("type")) {
                            case "ready":
                                if (seccion != null && seccion.equals(datos.optString("view"))) listo();
                                break;
                            case "auth-step":
                                if (seccion != null) {
                                    // No vence el tiempo mientras la persona escribe su clave/token.
                                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                                    listo();
                                }
                                break;
                            case "security-complete":
                                if ("seguridad".equals(seccion)) {
                                    String confirmada = datos.getJSONObject("session").toString();
                                    long epocaConfirmada = epocaSesion;
                                    sesionHilo.execute(() -> {
                                        if (isFinishing() || isDestroyed() || epocaConfirmada != SesionActual.epoca()) return;
                                        SesionActual.recibirSiVigente(getApplicationContext(), confirmada, epocaConfirmada);
                                        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { setResult(RESULT_OK); finish(); } });
                                    });
                                }
                                break;
                            case "open-module":
                                String destino = datos.optString("view");
                                if ("herramientas".equals(seccion) && destino.matches("roles|directivas|agenda|documentos|historial|reincorporacion|continuan"))
                                    startActivity(new Intent(this, WebActivity.class).putExtra(EXTRA_VISTA, destino));
                                break;
                            case "error": mostrarError("seguridad".equals(seccion)
                                    ? R.string.modulo_seguridad_error : R.string.modulo_no_disponible); break;
                            case "back": finish(); break;
                            case "session":
                                String nueva = datos.getJSONObject("session").toString();
                                long epocaMensaje = epocaSesion;
                                sesionHilo.execute(() -> {
                                    if (!isFinishing() && !isDestroyed())
                                        SesionActual.recibirSiVigente(getApplicationContext(), nueva, epocaMensaje);
                                });
                                break;
                            case "signed-out":
                                SesionActual.cerrarLocal(WebActivity.this);
                                if (seccion == null) epocaSesion = SesionActual.epoca();
                                if (seccion != null) mostrarError(R.string.error_sesion);
                                break;
                            default: break;
                        }
                    } catch (Exception ignored) { /* No se exponen datos de sesión en registros. */ }
                });
    }

    private int tituloModulo() {
        if (seccion == null) return R.string.app_name;
        switch (seccion) {
            case "cumplimiento": return R.string.acceso_cumplimiento;
            case "seguimiento": return R.string.historial_efectivo;
            case "panel": return R.string.panel_mensual;
            case "herramientas": return R.string.herramientas_titulo;
            case "reincorporacion": return R.string.modulo_reincorporacion;
            case "continuan": return R.string.modulo_continuan;
            case "roles": return R.string.modulo_roles;
            case "directivas": return R.string.modulo_directivas;
            case "agenda": return R.string.modulo_agenda;
            case "documentos": return R.string.modulo_documentos;
            case "historial": return R.string.modulo_historial;
            case "seguridad": return R.string.modulo_seguridad;
            case "efectivos": return R.string.acceso_personal;
            case "recepcion": return R.string.acceso_recepcion;
            case "recepcion-fisica": return R.string.recepcion_fisica;
            case "consulta": return R.string.consulta_expediente;
            case "registro": return R.string.modulo_registro;
            case "expedientes-lote": return R.string.modulo_expedientes_lote;
            default: return R.string.modulo_detalle;
        }
    }

    private void cargarModulo() {
        int solicitud = ++solicitudCarga;
        moduloListo = false;
        falloModulo = false;
        webView.stopLoading();
        webView.setVisibility(View.INVISIBLE);
        findViewById(R.id.cargaModulo).setVisibility(View.VISIBLE);
        findViewById(R.id.estadoPanel).setVisibility(View.GONE);
        findViewById(R.id.tvCargaModulo).setVisibility(View.VISIBLE);
        progreso.setVisibility(View.VISIBLE);
        handler.removeCallbacks(tiempoAgotado);
        handler.postDelayed(tiempoAgotado, 45000);
        if (!puenteDisponible || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            mostrarError(R.string.modulo_actualizar_webview); return;
        }
        sesionHilo.execute(() -> {
            try {
                String sesionVigente = seccion == null ? null : SesionActual.obtener(getApplicationContext());
                JSONObject config = new JSONObject().put("origin",ORIGEN_APP).put("path","/moral-y-disciplina/")
                        .put("nonce",nonce).put("view",seccion).put("session",sesionVigente)
                        .put("sessionKey",ConfigSupabase.CLAVE_SESION_WEB)
                        .put("note",getIntent().getStringExtra(EXTRA_NOTA_ID))
                        .put("document",documentoUrl).put("filename",getIntent().getStringExtra(EXTRA_NOMBRE_DOCUMENTO));
                String tema = asset("mobile_start.js");
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing() || solicitud != solicitudCarga) return;
                    if (epocaSesion != SesionActual.epoca()) { mostrarError(R.string.error_sesion); return; }
                    configuracionWeb = config;
                    scriptTema = tema;
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        instalarInicio();
                        webView.loadUrl(URL_APP);
                    } else {
                        mostrarError(R.string.modulo_actualizar_webview);
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> {
                if (!isDestroyed() && !isFinishing() && solicitud == solicitudCarga) mostrarError(Errores.mensaje(e));
            }); }
        });
    }

    private boolean temaOscuro() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void instalarInicio() {
        if (configuracionWeb == null || scriptTema == null) return;
        try { configuracionWeb.put("dark", temaOscuro()); } catch (org.json.JSONException ignored) { }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (scriptInicio != null) scriptInicio.remove();
            scriptInicio = WebViewCompat.addDocumentStartJavaScript(webView,
                    "window.__faltosConfig=" + configuracionWeb + ";\n" + scriptTema, Collections.singleton(ORIGEN_APP));
        }
    }

    @Override public void onConfigurationChanged(Configuration configuracion) {
        super.onConfigurationChanged(configuracion);
        if (webView == null) return;
        aplicarTemaNativo();
        instalarInicio();
        // Cambiar de tema nunca descarta lo escrito, el PDF preparado ni la vista actual.
        webView.evaluateJavascript("window.__faltosSetTheme&&window.__faltosSetTheme(" + temaOscuro() + ")", null);
    }

    private void aplicarTemaNativo() {
        findViewById(R.id.main).setBackgroundColor(getColor(R.color.fondo_app));
        findViewById(R.id.cargaModulo).setBackgroundColor(getColor(R.color.fondo_app));
        ((View)findViewById(R.id.tvModuloTitulo).getParent()).setBackgroundColor(getColor(R.color.superficie_tarjeta));
        ((TextView)findViewById(R.id.tvModuloTitulo)).setTextColor(getColor(R.color.texto_principal));
        ((TextView)findViewById(R.id.tvCargaModulo)).setTextColor(getColor(R.color.texto_principal));
        ((ImageButton)findViewById(R.id.btnAtras)).setImageTintList(ColorStateList.valueOf(getColor(R.color.texto_principal)));
        com.google.android.material.card.MaterialCardView panel = findViewById(R.id.estadoPanel);
        panel.setCardBackgroundColor(getColor(R.color.estado_alerta));
        panel.setStrokeColor(getColor(R.color.borde_alerta));
        ((TextView)findViewById(R.id.estadoTitulo)).setTextColor(getColor(R.color.texto_alerta));
        ((TextView)findViewById(R.id.estadoMensaje)).setTextColor(getColor(R.color.texto_alerta));
        com.google.android.material.button.MaterialButton reintentar = findViewById(R.id.btnReintentar);
        reintentar.setTextColor(getColor(R.color.verde_pnp));
        reintentar.setStrokeColor(ColorStateList.valueOf(getColor(R.color.borde_suave)));
        progreso.setIndicatorColor(getColor(R.color.verde_pnp));
        progreso.setTrackColor(getColor(R.color.verde_suave));
        webView.setBackgroundColor(getColor(R.color.fondo_app));
    }

    private void listo() {
        if (isDestroyed() || isFinishing() || falloModulo) return;
        moduloListo = true;
        handler.removeCallbacks(tiempoAgotado);
        findViewById(R.id.cargaModulo).setVisibility(View.GONE);
        progreso.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void mostrarError(int mensaje) {
        if (isDestroyed() || isFinishing()) return;
        falloModulo = true;
        handler.removeCallbacks(tiempoAgotado);
        webView.setVisibility(View.INVISIBLE);
        findViewById(R.id.cargaModulo).setVisibility(View.VISIBLE);
        progreso.setVisibility(View.GONE);
        findViewById(R.id.tvCargaModulo).setVisibility(View.GONE);
        Diseno.error(this,R.string.modulo_error,mensaje,true);
    }

    private String asset(String nombre) throws IOException {
        try (InputStream in = getAssets().open(nombre)) { return leer(in); }
    }

    private static String leer(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Recurso demasiado grande");
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private WebResourceResponse textoWeb(String tipo, String texto) {
        return new WebResourceResponse(tipo,"UTF-8",new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private WebResourceResponse recurso(Uri uri) {
        String url = uri.toString();
        try {
            if (url.equals(documentoUrl) && documento != null) {
                Map<String,String> headers = new HashMap<>();
                headers.put("Cache-Control","no-store");
                headers.put("Access-Control-Allow-Origin",ORIGEN_APP);
                return new WebResourceResponse("application/pdf", null, 200, "OK",
                        headers, getContentResolver().openInputStream(documento));
            }
            if (getPackageName().endsWith(".qa") && htmlPrueba != null && URL_APP.equals(url))
                return textoWeb("text/html",htmlPrueba);
            if ((URL_APP + "app.js").equals(uri.buildUpon().clearQuery().fragment(null).build().toString())) {
                String fuente;
                if (getPackageName().endsWith(".qa") && moduloPrueba != null) fuente = moduloPrueba;
                else {
                    HttpURLConnection conexion = (HttpURLConnection)new URL(url).openConnection();
                    conexion.setConnectTimeout(15000); conexion.setReadTimeout(20000);
                    conexion.setUseCaches(false);
                    conexion.setRequestProperty("Cache-Control", "no-cache");
                    conexion.setInstanceFollowRedirects(false);
                    try {
                        if (conexion.getResponseCode() != 200) throw new IOException("Módulo no disponible");
                        try (InputStream in = conexion.getInputStream()) { fuente = leer(in); }
                    } finally { conexion.disconnect(); }
                }
                return textoWeb("application/javascript",fuente + "\n" + asset("mobile_casework.js")
                        + "\n" + asset("mobile_adapter.js"));
            }
        } catch (Exception e) {
            runOnUiThread(() -> mostrarError(R.string.modulo_no_disponible));
            return new WebResourceResponse("text/plain","UTF-8",503,"Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }

    private void procesarDescarga(WebMessageCompat mensaje) {
        try {
            JSONObject datos = new JSONObject(mensaje.getData());
            String base64 = datos.getString("base64");
            if (base64.length() > MAX_BASE64) {
                avisar(getString(R.string.web_descarga_grande));
                return;
            }
            String nombre = nombreSeguro(datos.optString("nombre", "documento"));
            String tipo = tipoSeguro(datos.optString("tipo", "application/octet-stream"));
            descargas.execute(() -> {
                try {
                    String ubicacion = guardarEnDescargas(
                            Base64.decode(base64, Base64.DEFAULT), tipo, nombre);
                    avisar(getString(R.string.web_descarga_ok, nombre, ubicacion));
                } catch (IOException | IllegalArgumentException e) {
                    avisar(getString(R.string.web_descarga_error));
                }
            });
        } catch (Exception e) {
            avisar(getString(R.string.web_descarga_error));
        }
    }

    private static String nombreSeguro(String nombre) {
        String limpio = nombre == null ? "" : nombre.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return limpio.isEmpty() ? "documento" : limpio;
    }

    private static String tipoSeguro(String tipo) {
        return tipo != null && tipo.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")
                ? tipo : "application/octet-stream";
    }

    private String guardarEnDescargas(byte[] datos, String tipo, String nombre) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues valores = new ContentValues();
            valores.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
            valores.put(MediaStore.Downloads.MIME_TYPE, tipo);
            valores.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            valores.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri destino = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores);
            if (destino == null) {
                throw new IOException("Sin destino");
            }
            try {
                try (OutputStream salida = getContentResolver().openOutputStream(destino)) {
                    if (salida == null) throw new IOException("Sin salida");
                    salida.write(datos);
                }
                ContentValues lista = new ContentValues();
                lista.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(destino, lista, null, null);
            } catch (IOException | RuntimeException e) {
                getContentResolver().delete(destino, null, null);
                throw e;
            }
            return getString(R.string.web_carpeta_descargas);
        }
        File carpeta = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (carpeta == null || (!carpeta.exists() && !carpeta.mkdirs())) {
            throw new IOException("Sin almacenamiento");
        }
        File archivo = new File(carpeta, nombre);
        for (int copia = 1; !archivo.createNewFile(); copia++) {
            int punto = nombre.lastIndexOf('.');
            String base = punto > 0 ? nombre.substring(0, punto) : nombre;
            String extension = punto > 0 ? nombre.substring(punto) : "";
            archivo = new File(carpeta, base + " (" + copia + ")" + extension);
        }
        try (OutputStream salida = new FileOutputStream(archivo)) {
            salida.write(datos);
        } catch (IOException e) {
            archivo.delete();
            throw e;
        }
        return archivo.getAbsolutePath();
    }

}
