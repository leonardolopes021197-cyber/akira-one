package com.akira.one;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class SettingsActivity extends BaseActivity {
    private EditText panelUrl;
    private EditText currentAdultPin;
    private EditText newAdultPin;
    private EditText confirmAdultPin;
    private TextView result;
    private TextView currentUser;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

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

        findViewById(R.id.saveSettings).setOnClickListener(v -> savePanel());
        findViewById(R.id.testPanel).setOnClickListener(v -> {
            savePanel(false);
            testPanel();
        });
        findViewById(R.id.changeUser).setOnClickListener(v -> changeUser());
        findViewById(R.id.saveAdultPin).setOnClickListener(v -> changeAdultPin());
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
