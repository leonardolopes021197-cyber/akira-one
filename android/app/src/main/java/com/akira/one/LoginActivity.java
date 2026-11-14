package com.akira.one;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends BaseActivity {
    private EditText username;
    private EditText password;
    private TextView loginButton;
    private TextView status;
    private View progress;
    private boolean loggingIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // O app sempre abre nesta tela de usuário e senha. A única exceção é o modo
        // painel (URL de painel configurada), em que a lista vem do painel e não há login.
        boolean forceLogin = getIntent().getBooleanExtra("force_login", false);
        if (!forceLogin && Prefs.hasActivation(this) && !Prefs.directMode(this)) {
            openHome();
            return;
        }

        setContentView(R.layout.activity_login);
        username = findViewById(R.id.loginUser);
        password = findViewById(R.id.loginPass);
        loginButton = findViewById(R.id.loginButton);
        status = findViewById(R.id.loginStatus);
        progress = findViewById(R.id.loginProgress);

        loginButton.setOnClickListener(v -> doLogin());
        password.setOnEditorActionListener((v, actionId, event) -> {
            doLogin();
            return true;
        });

        // Sessão anterior do próprio app: já deixa usuário e senha preenchidos,
        // basta confirmar em ENTRAR (que também atualiza a playlist).
        boolean hasSavedLogin = !forceLogin && Prefs.hasActivation(this)
                && Prefs.isEmbeddedServer(Prefs.serverUrl(this));
        if (hasSavedLogin) {
            username.setText(Prefs.username(this));
            password.setText(Prefs.password(this));
            loginButton.requestFocus();
        } else {
            username.requestFocus();
        }
    }

    private void doLogin() {
        if (loggingIn) return;
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (user.isBlank() || pass.isBlank()) {
            status.setText("Informe usuário e senha.");
            if (user.isBlank()) username.requestFocus(); else password.requestFocus();
            return;
        }

        setLoading(true);
        status.setText("Conectando à Akira...");
        ServerPool.login(this, user, pass, new ServerPool.LoginCallback() {
            @Override public void onSuccess(String serverUrl) {
                setLoading(false);
                status.setText("✓ Acesso liberado");
                openLoading();
            }

            @Override public void onError(String message) {
                setLoading(false);
                status.setText(message);
                password.requestFocus();
                password.selectAll();
            }
        });
    }

    private void setLoading(boolean loading) {
        loggingIn = loading;
        loginButton.setEnabled(!loading);
        loginButton.setAlpha(loading ? 0.6f : 1f);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /** Mostra a tela de carregamento enquanto a playlist é atualizada. */
    private void openLoading() {
        Intent intent = new Intent(this, LoadingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
