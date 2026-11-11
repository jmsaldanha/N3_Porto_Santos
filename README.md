# LineupCerto — Gêmeo Digital do Porto de Santos (Checkpoint 1)

Front-end JavaFX do módulo **Portos** com dados mock em memória.

Universidade de Rio Verde (UniRV) — Programação Orientada a Objetos — Trabalho N3.

Requisitos: **JDK 25+** e **Maven 3.9+**. Execução (na raiz do projeto):

```
mvn clean javafx:run
```

## Origem dos dados

Os dados **não são inventados**: na inicialização, `SantosPortDataFactory` lê o script oficial
`schema_and_data_postgres.sql` (banco `lineup_santos`, embutido em `src/main/resources/org/example/seed/`)
e carrega em memória as tabelas do módulo Portos:

| Tabela | Registros |
|---|---|
| berths | 60 berços |
| ship_schedules | 153 atracações |
| registered_vessels | 152 navios |
| port_cargo_catalog | 15 commodities |
| tide_tables / tide_measurements | 3 meses × 1.440 medições (30 min) |

O front-end **não se conecta ao PostgreSQL** (RN022): o script é apenas a fonte dos dados mock.
Uma cópia do script também está em `database/schema_and_data_postgres.sql`.

## Escopo entregue (RF001 a RF012)

| Menu | Requisitos | O que demonstra |
|---|---|---|
| Lineup | RF001–RF004 | KPIs, 11 filtros de perfil, busca, cartões de berço (dias 1–30, maré baixa em vermelho, curva de maré), barras coloridas por status (RN005), paginação em lotes de 5 (setas ← → também), banner do navio selecionado |
| Berços | RF005–RF006 | CRUD dos 60 berços, filtro rápido, Editar/Excluir na linha, exclusão em cascata (RN024) |
| Cargas | RF007 | Catálogo das 15 commodities; alterar a taxa t/dia recalcula as próximas atracações do perfil (RN004) |
| Navios | RF008–RF009 | CRUD dos 152 navios, validação de IMO (RN007), busca por nome |
| Marés | RF010 | Troca do mês ativo (atualiza o Lineup), gráfico em Canvas, importação de CSV (exemplo em `exemplos/`) |
| Atracações | RF011–RF012 | Valida RN003 (perfil/calado), RN002 (maré ≤ 0,4 m com calado ≥ 13,5 m) e RN001 (sobreposição), sugere o próximo período livre; histórico com busca e exclusão (RN006 com perfil supervisor) |

## Arquitetura (MVC desacoplado)

```
org.example
├── App / Launcher             → View raiz (menu lateral + troca de telas)
├── view/                      → Telas JavaFX (LineupPane, BerthChartCard, *Pane, TideChart)
├── service/PortService        → Controller/Service: ObservableLists + regras de negócio
├── gateway/PortDataGateway    → Contrato de dados (interface)
│   └── MockPortDataGateway    → Implementação em memória (Checkpoint 1)
├── mock/                      → SantosPortDataFactory + SqlSeedReader (leitura do script oficial)
├── model/                     → Domínio encapsulado (Berth, ShipSchedule, RegisteredVessel,
│                                 CargoProduct, TideTable, TideMeasurement[record], enums)
└── dto/                       → Records imutáveis (BerthRequest, ShipScheduleRequest, ...)
```

Nenhuma tela acessa os dados diretamente. Na entrega final basta criar
`BackendApiClient implements PortDataGateway` (HTTP REST/JSON → Spring Boot) e trocar
uma linha em `App.start()`.
