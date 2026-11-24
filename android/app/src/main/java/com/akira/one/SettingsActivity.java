package com.akira.one;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends BaseActivity {
    private EditText panelUrl;
    private EditText currentAdultPin;
    private EditText newAdultPin;
    private EditText confirmAdultPin;
    private TextView result;
    private TextView currentUser;
    private ScrollView settingsScroll;
    private final List<SettingRow> rows = new ArrayList<>();

    private interface ValueProvider { String get(); }

    private static final class SettingRow {
        final int icon;
        final String title;
        final ValueProvider value;
        final Runnable action;
        TextView valueView;
        SettingRow(int icon, String title, ValueProvider value, Runnable action) {
            this.icon = icon; this.title = title; this.value = value; this.action = action;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        settingsScroll = findViewById(R.id.settingsScroll);
        panelUrl = findViewById(R.id.panelUrl);
        result = findViewById(R.id.testResult);
        currentUser = findViewById(R.id.currentUser);
        currentAdultPin = findViewById(R.id.currentAdultPin);
        newAdultPin = findViewById(R.id.newAdultPin);
        confirmAdultPin = findViewById(R.id.confirmAdultPin);

        panelUrl.setText(Prefs.panelUrl(this));
        currentUser.setText(Prefs.hasActivation(this)
                ? "Usuário conectado: " + Prefs.username(this)
                : "Nenhum usuário conectado");

        ((TextView) findViewById(R.id.settingsDeviceId))
                .setText("ID do dispositivo: " + DeviceIdentity.get(this));

        TextView appVersionValue = findViewById(R.id.appVersionValue);
        String versionName = AppVersion.name(this);
        long versionCode = AppVersion.code(this);
        appVersionValue.setText("Versão do aplicativo: "
                + (versionName.isBlank() ? "Desconhecida" : versionName)
                + " (" + versionCode + ")");

        findViewById(R.id.checkUpdate).setOnClickListener(v -> UpdateManager.checkGithub(this, true));
        findViewById(R.id.saveSettings).setOnClickListener(v -> savePanel());
        findViewById(R.id.testPanel).setOnClickListener(v -> {
            savePanel(false);
            testPanel();
        });
        findViewById(R.id.changeUser).setOnClickListener(v -> changeUser());
        findViewById(R.id.saveAdultPin).setOnClickListener(v -> changeAdultPin());

        // A conta atual entra na lista de contas salvas (para "Mudar lista de reprodução").
        if (Prefs.hasActivation(this)) Prefs.rememberAccount(this, Prefs.username(this), Prefs.password(this));
        buildPrefsGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshValues();
    }

    // ------------------------------------------------------------------ Grade de preferências

    private void buildPrefsGrid() {
        LinearLayout grid = findViewById(R.id.prefsGrid);
        rows.clear();
        rows.add(new SettingRow(R.drawable.ic_shield, "Controle dos Pais", () -> "Alterar a senha dos canais adultos", this::openParental));
        rows.add(new SettingRow(R.drawable.ic_list, "Mudar lista de reprodução", () -> "Conta atual: " + currentAccountName(), this::openChangeList));
        rows.add(new SettingRow(R.drawable.ic_list_plus, "Adicionar lista de reprodução", () -> "Entrar com outra conta", this::openAddList));
        rows.add(new SettingRow(R.drawable.ic_sort_az, "Classificação de canais ao vivo", () -> sortLabel(Prefs.liveSort(this)), this::openSortDialog));
        rows.add(new SettingRow(R.drawable.ic_radio, "Formato de transmissão ao vivo", () -> formatLabel(Prefs.liveFormatPref(this)), this::openFormatDialog));
        rows.add(new SettingRow(R.drawable.ic_eye_off, "Ocultar categorias ao vivo", () -> hiddenLabel("live"), () -> openHideCategories("live", "TV ao Vivo")));
        rows.add(new SettingRow(R.drawable.ic_eye_off, "Ocultar categorias de filmes", () -> hiddenLabel("movie"), () -> openHideCategories("movie", "Filmes")));
        rows.add(new SettingRow(R.drawable.ic_eye_off, "Ocultar categorias de séries", () -> hiddenLabel("series"), () -> openHideCategories("series", "Séries")));
        rows.add(new SettingRow(R.drawable.ic_history, "Limpar histórico de canais", () -> historyLabel("live", "canais"), () -> confirmClearHistory("live", "canais")));
        rows.add(new SettingRow(R.drawable.ic_history, "Limpar histórico de filmes", () -> historyLabel("movie", "filmes"), () -> confirmClearHistory("movie", "filmes")));
        rows.add(new SettingRow(R.drawable.ic_history, "Limpar histórico de séries", () -> historyLabel("series", "séries"), () -> confirmClearHistory("series", "séries")));
        rows.add(new SettingRow(R.drawable.ic_clock, "Ocultar relógio", () -> Prefs.hideClock(this) ? "Ativado" : "Desativado", this::toggleClock));
        rows.add(new SettingRow(R.drawable.ic_swap, "Permitir protocolo", () -> Prefs.allowCrossProtocol(this) ? "HTTP ↔ HTTPS: ativado" : "HTTP ↔ HTTPS: desativado", this::toggleProtocol));
        rows.add(new SettingRow(R.drawable.ic_refresh, "Atualizar lista agora", () -> "Baixar canais, filmes e séries de novo", this::refreshPlaylist));

        grid.removeAllViews();
        for (int i = 0; i < rows.size(); i += 2) {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            pair.setBaselineAligned(false);
            for (int j = 0; j < 2; j++) {
                if (i + j >= rows.size()) {
                    pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                    continue;
                }
                final SettingRow row = rows.get(i + j);
                View cell = getLayoutInflater().inflate(R.layout.item_setting_row, pair, false);
                ((ImageView) cell.findViewById(R.id.settingIcon)).setImageResource(row.icon);
                ((TextView) cell.findViewById(R.id.settingTitle)).setText(row.title);
                row.valueView = cell.findViewById(R.id.settingValue);
                cell.setOnClickListener(v -> row.action.run());
                pair.addView(cell);
            }
            grid.addView(pair);
        }
        refreshValues();
    }

    private void refreshValues() {
        for (SettingRow row : rows) {
            if (row.valueView == null) continue;
            String v = row.value.get();
            row.valueView.setText(v);
            row.valueView.setVisibility(v == null || v.isBlank() ? View.GONE : View.VISIBLE);
        }
    }

    private String currentAccountName() {
        return Prefs.hasActivation(this) ? Prefs.username(this) : "nenhuma";
    }

    private String sortLabel(String v) {
        if ("az".equals(v)) return "A → Z";
        if ("za".equals(v)) return "Z → A";
        return "Padrão do provedor";
    }

    private String formatLabel(String v) {
        if ("ts".equals(v)) return "TS";
        if ("m3u8".equals(v)) return "HLS (m3u8)";
        return "Automático";
    }

    private String hiddenLabel(String type) {
        int n = Prefs.hiddenCategories(this, type).size();
        return n == 0 ? "Nenhuma oculta" : n + (n == 1 ? " oculta" : " ocultas");
    }

    private String historyLabel(String type, String plural) {
        int n = Prefs.recent(this, type).size();
        return n == 0 ? "Não há " + plural + " vistos recentemente." : n + (n == 1 ? " item no histórico" : " itens no histórico");
    }

    // ------------------------------------------------------------------ Ações

    /** Leva até a seção de senha dos canais adultos, que já existe mais abaixo nesta tela. */
    private void openParental() {
        final View section = (View) currentAdultPin.getParent();
        settingsScroll.post(() -> {
            settingsScroll.smoothScrollTo(0, section.getTop());
            currentAdultPin.requestFocus();
        });
    }

    private void openSortDialog() {
        final String[] values = {"default", "az", "za"};
        final String[] labels = {"Padrão do provedor", "A → Z", "Z → A"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(Prefs.liveSort(this))) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Classificação de canais ao vivo")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.setLiveSort(this, values[which]);
                    refreshValues();
                    d.dismiss();
                    Toast.makeText(this, "Vale na próxima vez que abrir TV ao Vivo.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void openFormatDialog() {
        final String[] values = {"auto", "ts", "m3u8"};
        final String[] labels = {"Automático (recomendado)", "TS", "HLS (m3u8)"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(Prefs.liveFormatPref(this))) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Formato de transmissão ao vivo")
                .setMessage("O formato escolhido é tentado primeiro; se não funcionar, o app tenta os outros.")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.setLiveFormatPref(this, values[which]);
                    refreshValues();
                    d.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void openHideCategories(final String type, final String label) {
        JSONArray cached = CatalogCache.peekCategories(this, type);
        if (cached != null) {
            showHideDialog(type, label, cached);
            return;
        }
        Toast.makeText(this, "Carregando categorias…", Toast.LENGTH_SHORT).show();
        XtreamClient.getCategories(this, type, new XtreamClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray array) {
                CatalogCache.saveCategories(SettingsActivity.this, type, array);
                if (!isFinishing()) showHideDialog(type, label, array);
            }

            @Override public void onError(Exception error) {
                Toast.makeText(SettingsActivity.this, "Não foi possível carregar as categorias agora.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showHideDialog(final String type, String label, JSONArray array) {
        final List<String> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            ids.add(o.optString("category_id"));
            names.add(o.optString("category_name", "Categoria"));
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, "Nenhuma categoria encontrada.", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> hidden = Prefs.hiddenCategories(this, type);
        final boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) checked[i] = hidden.contains(ids.get(i));

        new AlertDialog.Builder(this)
                .setTitle("Ocultar categorias • " + label)
                .setMultiChoiceItems(names.toArray(new String[0]), checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Salvar", (d, w) -> {
                    Set<String> out = new HashSet<>();
                    for (int i = 0; i < ids.size(); i++) if (checked[i]) out.add(ids.get(i));
                    Prefs.setHiddenCategories(this, type, out);
                    refreshValues();
                    Toast.makeText(this, "Salvo. Vale na próxima vez que abrir a seção.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Mostrar todas", (d, w) -> {
                    Prefs.setHiddenCategories(this, type, new HashSet<>());
                    refreshValues();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmClearHistory(final String type, final String plural) {
        int n = Prefs.recent(this, type).size();
        if (n == 0) {
            Toast.makeText(this, "Não há " + plural + " vistos recentemente.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Limpar histórico de " + plural + "?")
                .setMessage("Serão removidos " + n + " itens da lista de assistidos recentemente.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Limpar", (d, w) -> {
                    Prefs.clearRecent(this, type);
                    refreshValues();
                    Toast.makeText(this, "Histórico limpo.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleClock() {
        Prefs.setHideClock(this, !Prefs.hideClock(this));
        refreshValues();
    }

    private void toggleProtocol() {
        Prefs.setAllowCrossProtocol(this, !Prefs.allowCrossProtocol(this));
        refreshValues();
    }

    private void refreshPlaylist() {
        if (!Prefs.hasActivation(this)) {
            Toast.makeText(this, "Entre com uma conta primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, LoadingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // ------------------------------------------------------------------ Listas (contas)

    private void openChangeList() {
        if (!Prefs.panelUrl(this).isBlank()) {
            startActivity(new Intent(this, ListsActivity.class)); // modo painel: as listas vêm do painel
            return;
        }
        final List<String[]> accounts = Prefs.savedAccounts(this);
        if (accounts.size() < 2) {
            Toast.makeText(this, "Só há uma lista salva. Use “Adicionar lista de reprodução”.", Toast.LENGTH_LONG).show();
            return;
        }
        final String current = Prefs.hasActivation(this) ? Prefs.username(this) : "";
        final String[] labels = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            String u = accounts.get(i)[0];
            labels[i] = u.equals(current) ? u + "   ✓ em uso" : u;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Mudar lista de reprodução")
                .setItems(labels, (d, which) -> {
                    String[] acc = accounts.get(which);
                    if (acc[0].equals(current)) return;
                    loginAndSwitch(acc[0], acc[1]);
                })
                .setNegativeButton("Fechar", null)
                .create();
        dialog.show();
        // Segurar OK numa lista remove ela do aparelho (a que está em uso não pode ser removida).
        dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            final String u = accounts.get(position)[0];
            if (u.equals(current)) {
                Toast.makeText(this, "Esta é a lista em uso.", Toast.LENGTH_SHORT).show();
                return true;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Remover lista?")
                    .setMessage("Remover “" + u + "” deste aparelho? Você poderá adicionar de novo depois.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover", (d2, w) -> {
                        Prefs.removeAccount(this, u);
                        dialog.dismiss();
                        Toast.makeText(this, "Lista removida.", Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return true;
        });
    }

    private void openAddList() {
        if (!Prefs.panelUrl(this).isBlank()) {
            Toast.makeText(this, "No modo painel, as listas são gerenciadas pelo painel.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText user = dialogField("Usuário", false);
        final EditText pass = dialogField("Senha", true);
        box.addView(user);
        box.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("Adicionar lista de reprodução")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Adicionar", (d, w) ->
                        loginAndSwitch(user.getText().toString().trim(), pass.getText().toString()))
                .show();
    }

    private EditText dialogField(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(password
                ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                : android.text.InputType.TYPE_CLASS_TEXT);
        e.setTextColor(0xFFF7F8FB);
        e.setHintTextColor(0xFF8A8A8A);
        e.setBackgroundResource(R.drawable.bg_input_login);
        int h = (int) (16 * getResources().getDisplayMetrics().density);
        e.setPadding(h, 0, h, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (50 * getResources().getDisplayMetrics().density));
        lp.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        e.setLayoutParams(lp);
        return e;
    }

    /** Valida a conta, passa a usá-la e abre a tela de carregamento da nova playlist. */
    private void loginAndSwitch(final String user, final String pass) {
        if (user.isBlank() || pass.isEmpty()) {
            Toast.makeText(this, "Informe usuário e senha.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String previous = Prefs.hasActivation(this) ? Prefs.username(this) : "";
        final AlertDialog wait = new AlertDialog.Builder(this)
                .setTitle("Conectando…")
                .setMessage("Validando usuário e senha…")
                .setCancelable(false)
                .create();
        wait.show();
        ServerPool.login(this, user, pass, new ServerPool.LoginCallback() {
            @Override public void onSuccess(String serverUrl) {
                wait.dismiss();
                Prefs.rememberAccount(SettingsActivity.this, user, pass);
                if (!user.equals(previous)) CatalogCache.clearAll(SettingsActivity.this);
                Intent i = new Intent(SettingsActivity.this, LoadingActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }

            @Override public void onError(String message) {
                wait.dismiss();
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void changeAdultPin() {
        String current = currentAdultPin.getText().toString().trim();
        String next = newAdultPin.getText().toString().trim();
        String confirm = confirmAdultPin.getText().toString().trim();

        if (!Prefs.verifyAdultPin(this, current)) {
            Toast.makeText(this, "Senha atual incorreta.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!next.matches("\\d{4}")) {
            Toast.makeText(this, "A nova senha deve ter exatamente 4 números.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!next.equals(confirm)) {
            Toast.makeText(this, "A confirmação da nova senha não confere.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Prefs.setAdultPin(this, next)) {
            currentAdultPin.setText("");
            newAdultPin.setText("");
            confirmAdultPin.setText("");
            Toast.makeText(this, "Senha dos canais adultos alterada.", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePanel() { savePanel(true); }

    private void savePanel(boolean showToast) {
        String old = Prefs.panelUrl(this);
        Prefs.setPanelUrl(this, panelUrl.getText().toString().trim());
        if (!old.equals(Prefs.panelUrl(this))) Prefs.setDeviceToken(this, "");
        if (showToast) {
            Toast.makeText(this,
                    Prefs.panelUrl(this).isBlank()
                            ? "Modo direto com 4 servidores Akira ativado."
                            : "URL do painel salva.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void changeUser() {
        CatalogCache.clearAll(this);
        Prefs.clearActivation(this);
        Prefs.saveLists(this, null);
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra("force_login", true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void testPanel() {
        if (Prefs.panelUrl(this).isBlank()) {
            result.setText("✓ Modo direto ativo • 4 servidores Akira integrados no aplicativo");
            return;
        }
        result.setText("Testando painel...");
        ApiClient.health(this, new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) {
                result.setText("✓ Painel online • " + json.optString("service", "Akira Manager"));
            }

            public void onError(Exception e) {
                result.setText("✕ Não foi possível acessar: " + e.getMessage());
            }
        });
    }
}
