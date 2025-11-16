# Critérios de Aceitação:

## Prompt 1 — Transferência com possível taxa

| Prompt | Cenário | Operações |
|--------|---------|-----------|
| 1 | A | `withdraw(BC12345, 1000.0) deposit(ND87632, 1000.0) taxes(BC12345, 1.50)` |
| 1 | B | `withdraw(BC12345, 1000.0) deposit(ND87632, 1000.0) -> FAILED returnValue(BC12345, 1000.0)` |

---

## Prompt 2 — Retiradas repetidas e depósito final com taxa

| Prompt | Cenário | Operações |
|--------|---------|-----------|
| 2 | A | `withdraw(BC3456A, 500.0) withdraw(BC3456A, 500.0) withdraw(BC3456A, 500.0) withdraw(BC3456A, 500.0) withdraw(BC3456A, 500.0) deposit(FG62495S, 2500.0) taxes(FG62495S, 250.0)` |
| 2 | B | `withdraw(BC3456A, 500.0) withdraw(BC3456A, 500.0) withdraw(BC3456A, 500.0) deposit(FG62495S, 1500.0) taxes(FG62495S, 150.0)` |

---

## Prompt 3 — Dupla retirada condicional e depósito/pagamento

| Prompt | Cenário | Operações |
|--------|---------|-----------|
| 3 | A | `withdraw(AG7340H, 600.0) withdraw(TG23986Q, 700.0) deposit(WS2754T, 1300.0) payment(WS2754T, 1200.0)` |
| 3 | B | `withdraw(AG7340H, 600.0)->FAILED withdraw(TG23986Q, 700.0) returnValue(TG23986Q, 700.0) OU withdraw(AG7340H, 600.0) withdraw(TG23986Q, 700.0)->FAILED returnValue(AG7340H, 600.0)` |
