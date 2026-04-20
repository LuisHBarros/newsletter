# Access Service

Lambda function para gerenciamento de permissões de conteúdo usando DynamoDB.

## Funcionalidades

- **Grant** (`action: grant`): Concede acesso de um usuário a um conteúdo
- **Revoke** (`action: revoke`): Revoga acesso de um usuário a um conteúdo  
- **Check** (`action: check`): Verifica se usuário tem acesso a um conteúdo
- **List** (`action: list`): Lista todos os acessos ativos de um usuário

## Estrutura

```
access/
├── main.go              # Entrypoint Lambda
├── handler.go           # HTTP handler + lógica de permissões
├── permissions/         # DynamoDB store
│   └── store.go
├── internal/            # Configurações
│   └── config.go
├── tracing/             # X-Ray tracing
│   └── xray.go
├── template.yaml        # SAM template
└── go.mod
```

## API

### POST /access

**Request:**
```json
{
  "action": "grant",
  "userId": "user-123",
  "contentId": "content-456",
  "planId": "plan-pro",
  "duration": "30d"
}
```

**Response:**
```json
{
  "success": true,
  "permission": {
    "userId": "user-123",
    "contentId": "content-456",
    "planId": "plan-pro",
    "grantedAt": "2026-04-20T12:30:00Z",
    "expiresAt": "2026-05-20T12:30:00Z"
  }
}
```

### Ações

| Action | Campos obrigatórios | Descrição |
|--------|-------------------|-----------|
| `grant` | `userId`, `contentId` | Concede acesso. Opcional: `duration` (e.g., `7d`, `30d`, `forever`) |
| `revoke` | `userId`, `contentId` | Revoga acesso |
| `check` | `userId`, `contentId` | Retorna `hasAccess: true/false` |
| `list` | `userId` | Lista todas as permissões ativas do usuário |

## Deploy

```bash
sam build
sam deploy --guided
```

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `LOG_LEVEL` | `info` | Nível de log (debug, info, warn, error) |
| `PERMISSIONS_TABLE` | `content-permissions` | Nome da tabela DynamoDB |
| `CONTENT_SERVICE_URL` | `` | URL do serviço de conteúdo |

## DynamoDB Schema

**Tabela:** `content-permissions-{environment}`

| Atributo | Tipo | Descrição |
|----------|------|-----------|
| `userId` | PK (S) | ID do usuário |
| `contentId` | SK (S) | ID do conteúdo |
| `planId` | S | ID do plano associado |
| `grantedAt` | S | Data de concessão |
| `expiresAt` | S | Data de expiração (opcional) |
| `ttl` | N | TTL para expiração automática |

**Features:**
- On-demand capacity
- TTL habilitado para expiração automática
- Point-in-time recovery
