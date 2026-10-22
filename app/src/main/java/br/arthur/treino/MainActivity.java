package br.arthur.treino;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// A tela do app fica dentro do APK (assets/index.html); a internet só é usada
// para falar com a planilha, carregar as fotos e baixar atualizações.
public class MainActivity extends Activity {

    private WebView web;
    private String urlPendente; // atualização esperando a permissão de instalar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(0xFF0F172A);
        web.addJavascriptInterface(new Ponte(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });

        setContentView(web);
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Voltou da tela de permissão: continua a atualização que estava esperando
        if (urlPendente != null && podeInstalar()) {
            String url = urlPendente;
            urlPendente = null;
            baixarEInstalar(url);
            return;
        }
        // Ao voltar para o app, tenta enviar treinos guardados sem internet
        web.evaluateJavascript("window.aoVoltar && window.aoVoltar()", null);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.voltar ? window.voltar() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    // ---------- atualização ----------

    private boolean podeInstalar() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void avisarJs(String funcao, String arg) {
        String js = "window." + funcao + " && window." + funcao + "(" + (arg == null ? "null" : "'" + arg.replace("'", "\\'") + "'") + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private void baixarEInstalar(String url) {
        if (!podeInstalar()) {
            // Android pede uma vez para liberar instalações vindas deste app
            urlPendente = url;
            avisarJs("fimAtualizacao", "Libere \"Permitir desta fonte\" e volte para o app.");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        new Thread(() -> {
            File destino = new File(new File(getCacheDir(), "atualizacao"), "Treino.apk");
            try {
                destino.getParentFile().mkdirs();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                int total = c.getContentLength();
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(destino)) {
                    byte[] buf = new byte[16384];
                    int n, lido = 0, ultimoPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        lido += n;
                        if (total > 0) {
                            int pct = (int) (lido * 100L / total);
                            if (pct != ultimoPct) { ultimoPct = pct; avisarJs("progressoAtualizacao", String.valueOf(pct)); }
                        }
                    }
                }
                Uri apk = FileProvider.getUriForFile(this, getPackageName() + ".arquivos", destino);
                Intent instalar = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apk, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                runOnUiThread(() -> startActivity(instalar));
                avisarJs("fimAtualizacao", null);
            } catch (Exception e) {
                avisarJs("fimAtualizacao", "Não deu para baixar a atualização. Tente de novo com internet.");
            }
        }).start();
    }

    // Funções que a tela (JavaScript) pode chamar
    private class Ponte {
        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public int versao() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public void atualizar(String url) {
            runOnUiThread(() -> baixarEInstalar(url));
        }
    }
}
