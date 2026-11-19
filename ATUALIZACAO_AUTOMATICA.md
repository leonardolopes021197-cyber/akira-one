# Atualização automática do AKIRA ONE

Fluxo: você envia o projeto para o GitHub → o GitHub compila → publica a versão no repositório
**público de releases** → o app, ao abrir, vê que há versão nova, baixa e pede para instalar.

> O Android sempre pede uma confirmação final ("Instalar"). Instalação totalmente silenciosa
> só existe com root, então o app baixa sozinho, mas o usuário toca em Instalar.

## Configuração (uma vez só)

1. **Crie um repositório público** chamado `akira-one-releases` (marque *Add a README*).
   Ele guarda só os APKs, nunca o código-fonte.
2. **Crie uma chave de acesso** (Settings → Developer settings → Fine-grained tokens) com acesso aos
   repositórios `akira-one` e `akira-one-releases` e estas permissões:
   `Contents: Read and write`, `Workflows: Read and write`, `Actions: Read`, `Secrets: Read and write`.
3. **Salve a chave como segredo** no repositório privado `akira-one`:
   Settings → Secrets and variables → Actions → New repository secret →
   nome `RELEASES_TOKEN`, valor = a chave do passo 2.
4. Envie qualquer versão nova. Pronto.

## Como lançar uma atualização

1. Suba o `versionCode` e o `versionName` em `android/app/build.gradle.kts`
   (o `versionCode` SEMPRE precisa aumentar, senão o app não enxerga como nova).
2. Escreva a primeira linha do `CHANGELOG.md` como `# AKIRA ONE x.y.z — título`.
   Esse título aparece no aviso de atualização.
3. Envie para o GitHub. Em poucos minutos a versão aparece em *Releases* do repositório público
   e todos os aparelhos recebem o aviso ao abrir o app.

## Itens editáveis

| O quê | Onde |
|---|---|
| Nome do repositório público | `RELEASES_REPO` em `UpdateManager.java` **e** no topo de `.github/workflows/build-apk.yml` |
| Forçar a atualização (sem botão "Depois") | `"mandatory": False` → `True` no passo do `version.json` do workflow |
| Botão manual | Configurações → Sobre → *Verificar atualização* |

## Chave de assinatura (IMPORTANTE)

O arquivo `android/akira-signing.jks` é a assinatura do app. O Android só aceita atualizar por cima
se a assinatura for a mesma, então **guarde uma cópia em local seguro** (se perder, todos os usuários
terão que desinstalar e instalar de novo). Quem tem esse arquivo consegue publicar atualizações,
por isso o repositório do código deve continuar **privado**.
