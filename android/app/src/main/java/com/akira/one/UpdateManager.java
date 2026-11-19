package com.akira.one;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Atualização automática do AKIRA ONE.
 *
 * Como funciona: o GitHub Actions publica cada versão no repositório PÚBLICO de releases
 * (RELEASES_REPO) junto com um version.json. Ao abrir o app, ele lê esse arquivo; se a versão
 * for maior que a instalada, avisa, baixa o APK com barra de progresso e abre o instalador do
 * Android. O Android sempre pede uma confirmação final do usuário (não existe instalação 100%
 * silenciosa sem root).
 *
 * EDITE AQUI: o repositório de releases. Formato: "dono/repositorio".
 */
public final class UpdateManager {
    public static final String RELEASES_REPO = "leonardolopes021197-cyber/akira-one-releases";

    private static final String BASE = "https://github.com/" + RELEASES_REPO + "/releases/latest/download/";
    private static boolean checkedThisProcess = false;

    private UpdateManager() {}

    // ------------------------------------------------------------------ GitHub

    /** Verifica uma vez por abertura do app, sem incomodar se não houver nada novo. */
    public static void checkOnStartup(Activity a) {
        if (checkedThisProcess) return;
        checkedThisProcess = true;
        checkGithub(a, false);
    }

    /** userInitiated = true (botão em Configurações): também avisa quando já está atualizado ou deu erro. */
    public static void checkGithub(final Activity a, final boolean userInitiated) {
        if (userInitiated) toast(a, "Verificando atualizações…");
        new Thread(() -> {
            JSONObject info = null;
            try {
                info = new JSONObject(httpGetText(BASE + "version.json"));
            } catch (Exception ignored) {
                // sem internet, repositório ainda sem release, etc.
            }
            final JSONObject result = info;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (result == null) {
                    if (userInitiated) toast(a, "Não foi possível verificar atualizações agora.");
                    return;
                }
                long code = result.optLong("versionCode", 0L);
                if (code <= AppVersion.code(a)) {
                    if (userInitiated) toast(a, "Você já está na versão mais recente (" + AppVersion.name(a) + ").");
                    return;
                }
                String name = result.optString("versionName", "");
                String apk = result.optString("apk", "AKIRA_ONE.apk");
                String notes = result.optString("notes", "");
                showDialog(a, name, notes, BASE + apk, result.optBoolean("mandatory", false));
            });
        }, "akira-update-check").start();
    }

    // ------------------------------------------------------------------ Painel (modo antigo)

    /** Atualização anunciada pelo painel PHP (modo painel). */
    public static void check(Activity a, JSONObject u) {
        long code = u.optLong("version_code", 0L);
        String url = u.optString("apk_url", "");
        if (code <= AppVersion.code(a) || url.isBlank()) return;
        String msg = u.optString("message", "Há uma nova versão da Akira TV disponível.");
        showDialog(a, "", msg, url, false);
    }

    // ------------------------------------------------------------------ Diálogo e download

    private static void showDialog(Activity a, String versionName, String notes, String apkUrl, boolean mandatory) {
        StringBuilder msg = new StringBuilder();
        if (!versionName.isBlank()) {
            msg.append("Nova versão ").append(versionName).append(" disponível (você usa ")
                    .append(AppVersion.name(a)).append(").");
        }
        if (!notes.isBlank()) {
            if (msg.length() > 0) msg.append("\n\n");
            msg.append(notes);
        }
        if (msg.length() == 0) msg.append("Há uma nova versão da Akira TV disponível.");

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle("Atualização disponível")
                .setMessage(msg.toString())
                .setCancelable(!mandatory)
                .setPositiveButton("Atualizar agora", (d, w) -> startUpdate(a, apkUrl));
        if (!mandatory) b.setNegativeButton("Depois", null);
        b.show();
    }

    private static void startUpdate(Activity a, String apkUrl) {
        // Android 8+: o app precisa da permissão "Instalar apps desconhecidos" (uma vez só).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("Permissão necessária")
                    .setMessage("Para instalar a atualização, ative “Permitir desta fonte” para a Akira TV na próxima tela. "
                            + "Depois volte ao app e toque em Atualizar de novo.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Abrir configuração", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + a.getPackageName()));
                        a.startActivity(i);
                    })
                    .show();
            return;
        }
        download(a, apkUrl);
    }

    private static void download(final Activity a, final String apkUrl) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad / 2);
        final TextView label = new TextView(a);
        label.setText("Baixando atualização…  0%");
        label.setTextColor(0xFFF7F8FB);
        label.setTextSize(15);
        label.setGravity(Gravity.START);
        final ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressDrawable(a.getDrawable(R.drawable.progress_akira));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (8 * a.getResources().getDisplayMetrics().density));
        lp.topMargin = (int) (14 * a.getResources().getDisplayMetrics().density);
        box.addView(label);
        box.addView(bar, lp);

        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Atualizando Akira TV")
                .setView(box)
                .setCancelable(false)
                .create();
        dialog.show();

        new Thread(() -> {
            File dir = a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = a.getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            final File apk = new File(dir, "akira-update.apk");
            final File part = new File(dir, "akira-update.apk.part");
            boolean ok = false;
            try {
                HttpURLConnection c = open(apkUrl);
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                long total = c.getContentLength();
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[32 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            final int percent = (int) (done * 100L / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                a.runOnUiThread(() -> {
                                    bar.setProgress(percent);
                                    label.setText("Baixando atualização…  " + percent + "%");
                                });
                            }
                        }
                    }
                }
                if (apk.exists()) apk.delete();
                ok = part.renameTo(apk) && apk.length() > 0;
            } catch (Exception ignored) {
                ok = false;
            }
            final boolean success = ok;
            a.runOnUiThread(() -> {
                try { dialog.dismiss(); } catch (Exception ignored) {}
                if (a.isFinishing() || a.isDestroyed()) return;
                if (success) install(a, apk);
                else toast(a, "Falha ao baixar a atualização. Tente novamente.");
            });
        }, "akira-update-download").start();
    }

    private static void install(Activity a, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            toast(a, "Não foi possível abrir o instalador.");
        }
    }

    // ------------------------------------------------------------------ HTTP

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true); // o GitHub redireciona o download para outro host (https → https)
        c.setRequestProperty("User-Agent", "AkiraTV-Updater");
        return c;
    }

    private static String httpGetText(String url) throws Exception {
        HttpURLConnection c = open(url);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void toast(Activity a, String message) {
        Toast.makeText(a, message, Toast.LENGTH_LONG).show();
    }
}
