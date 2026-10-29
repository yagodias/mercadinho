# Lista de Mercado

App Android de lista de compras compartilhada, com aviso quando alguém do grupo chega num mercado e ainda tem itens pendentes.

- **Lista compartilhada em tempo real** entre todos do grupo (Firebase Firestore)
- **Funciona sem sinal** dentro do mercado; sincroniza quando a internet volta
- **Aviso de mercado** mesmo com o app fechado (Geofencing do Android)
- **Sem Play Store**: você gera o APK e instala direto nos celulares
- **Custo zero**: cabe no plano gratuito do Firebase; mapa do OpenStreetMap, sem chave de API

---

## 1. Configurar o Firebase (uma vez, ~10 min)

1. Acesse [console.firebase.google.com](https://console.firebase.google.com) e clique em **Criar projeto**. Pode desativar o Google Analytics.
2. Na página do projeto, clique no ícone do **Android** para adicionar um app:
   - **Nome do pacote:** `br.com.listamercado` (tem que ser exatamente esse)
   - Apelido e SHA-1 podem ficar em branco
   - Baixe o arquivo **`google-services.json`** e coloque na pasta **`app/`** deste projeto
   - Pule os passos de "adicionar SDK" (já está feito no código)
3. Menu **Build > Authentication** → **Vamos começar** → aba **Método de login** → **Anônimo** → ativar → Salvar.
4. Menu **Build > Firestore Database** → **Criar banco de dados**:
   - Local: `southamerica-east1 (São Paulo)`
   - Modo: **produção**
5. Ainda no Firestore, aba **Regras**: apague o que estiver lá, cole todo o conteúdo do arquivo [`firestore.rules`](firestore.rules) e clique em **Publicar**.

> As regras garantem que só quem está no grupo lê e escreve a lista dele. Sem publicar, o app mostra erro de "acesso recusado".

## 2. Gerar o APK

1. Instale o [Android Studio](https://developer.android.com/studio) e abra **esta pasta** (`ListaMercado`).
2. Espere o "Gradle sync" terminar (na primeira vez baixa bastante coisa). Se ele sugerir atualizar versões de plugins/bibliotecas, pode aceitar.
3. Menu **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. Quando terminar, clique em **locate**. O arquivo é `app/build/outputs/apk/debug/app-debug.apk`.
5. Mande o APK para cada celular (WhatsApp, Drive, cabo USB) e toque nele para instalar. O Android vai pedir para **permitir instalar apps desta fonte**.

Alternativa: com o celular ligado por USB e a [depuração USB ativada](https://developer.android.com/studio/debug/dev-options), é só clicar em **Run ▶** no Android Studio.

## 3. Primeiro uso

1. **Quem cria:** abre o app, digita o nome, escolhe **Criar grupo**.
2. **Os outros:** tocam em **Tenho um código** e digitam o código de 6 letras. Para ver ou enviar o código, use o botão de compartilhar no topo da lista.
3. Na aba **Lista**, siga o cartão **"Ative o aviso de mercado"** até ele sumir. Os pontos importantes:
   - Localização: escolha **"Permitir o tempo todo"** (sem isso o aviso só funciona com o app aberto)
   - Notificações: permitir
   - Economia de bateria: liberar
4. Na aba **Mercados**, toque em **+ Mercado**, arraste o mapa até o pino ficar sobre o mercado, dê um nome e salve. Todos do grupo passam a receber o aviso nele.

## Como o aviso funciona

- Cada celular vigia os mercados sozinho; só quem está no mercado recebe o aviso.
- O aviso sai depois de **~2 minutos dentro do raio**, para não disparar quando você só passa na frente.
- Só avisa se houver **itens não riscados** na lista.
- No máximo **um aviso por mercado a cada 2 horas**.
- Depois de reiniciar o celular, os mercados são recadastrados automaticamente.

O tempo de espera e o intervalo entre avisos ficam em:
- `LOITERING_MS` em `geo/GeofenceManager.kt`
- `COOLDOWN_MS` em `geo/GeofenceReceiver.kt`

## Se o aviso não aparecer

Algumas marcas fecham apps em segundo plano por conta própria, mesmo com tudo permitido:

| Marca | O que ajustar |
|---|---|
| **Xiaomi / Redmi / Poco** | Configurações > Apps > Lista de Mercado: ativar **Início automático** e em **Economia de bateria** escolher **Sem restrições** |
| **Samsung** | Configurações > Bateria > Limites de uso em segundo plano: tirar o app de "Apps em suspensão"; nas configurações do app, Bateria > **Sem restrições** |
| **Motorola / outras** | Configurações do app > Bateria > **Sem restrições** |

Outros pontos:
- A **localização do celular** precisa estar ligada.
- Raio pequeno (100 m) em mercado grande pode não disparar; prefira 150 m ou 250 m.
- O Android pode levar alguns minutos para perceber a chegada, é normal.

## Bom saber

- **Login anônimo:** não tem cadastro nem senha. Se alguém desinstalar o app, é só entrar de novo com o código do grupo.
- **Limite:** o Android monitora até 100 mercados por app.
- **Sair do grupo:** menu ⋮ no topo. Para voltar, usa o código de novo.

## Estrutura do código

```
app/src/main/java/br/com/listamercado/
├── App.kt                  inicialização (canal de notificação, mapa)
├── MainActivity.kt         decide entre tela inicial e app
├── Notifier.kt             monta a notificação do mercado
├── data/
│   ├── Models.kt           Group, Item, Store
│   ├── Prefs.kt            dados locais do aparelho
│   └── Repository.kt       tudo que fala com o Firestore
├── geo/
│   ├── GeofenceManager.kt  registra os mercados no Android
│   ├── GeofenceReceiver.kt recebe a chegada e dispara o aviso
│   └── BootReceiver.kt     recadastra após reiniciar
└── ui/
    ├── AppViewModel.kt     estado e ações do app
    ├── OnboardingScreen.kt criar / entrar no grupo
    ├── MainScreen.kt       abas, convite, sair do grupo
    ├── ListScreen.kt       a lista
    ├── StoresScreen.kt     mercados cadastrados
    ├── MapPickerScreen.kt  marcar mercado no mapa
    └── AlertsSetupCard.kt  guia de permissões
```

Dados no Firestore:

```
invites/{codigo}              → { groupId }
groups/{id}                   → { name, inviteCode, members, memberNames }
groups/{id}/items/{itemId}    → { name, checked, addedBy, createdAt }
groups/{id}/stores/{storeId}  → { name, lat, lng, radius, addedBy, createdAt }
```

Stack: Kotlin, Jetpack Compose (Material 3), Firebase Firestore + Auth, Google Play Services Location, osmdroid. Android 8.0 ou superior.
