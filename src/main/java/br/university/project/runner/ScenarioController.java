package br.university.project.runner;

import br.university.project.model.OperationType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller que decide se cada operação deve Suceder/Fracassar conforme cenário.
 * Você irá configurar este controller antes de cada execução.
 */
public class ScenarioController {

    public enum Scenario { P1A, P1B, P2A, P2B, P3A, P3B }

    private Scenario scenario;
    // counters por conta (útil para PROMPT2)
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public ScenarioController(Scenario scenario) {
        this.scenario = scenario;
    }

    public boolean simulate(OperationType type, String account, double value) {
        // regras simples baseadas nos cenários descritos no enunciado
        switch (scenario) {
            case P1A:
                // tudo ok
                return true;
            case P1B:
                // saque OK, deposit fail -> deposit oscalls devem ser tratados abaixo na lógica externa
                if (type == OperationType.DEPOSIT) return false;
                return true;
            case P2A:
                // 5 withdraws OK
                return true;
            case P2B:
                // primeiro 3 withdraws ok, a partir do 4o falha
                if (type == OperationType.WITHDRAW) {
                    int attempts = counters.computeIfAbsent(account, k -> new AtomicInteger(0)).incrementAndGet();
                    return attempts <= 3;
                }
                return true; // outras operações ok
            case P3A:
                return true; // ambos os withdraws ok
            case P3B:
                // force one withdraw to fail: define por conta (por simplicidade, se account endsWith 'H' succeed else fail)
                if (type == OperationType.WITHDRAW) {
                    return account.endsWith("H"); // ajuste conforme descrição do experimento
                }
                return true;
            default:
                return true;
        }
    }
}