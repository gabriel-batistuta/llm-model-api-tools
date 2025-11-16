package br.university.project.runner;

import br.university.project.model.OperationType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller que decide se cada operação deve Suceder/Fracassar conforme cenário.
 */
public class ScenarioController {

    public enum Scenario { P1A, P1B, P2A, P2B, P3A, P3B }

    private Scenario scenario;
    // counters por conta (útil para PROMPT2)
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private boolean p1bDepositFailed = false;
    private boolean p3bFirstWithdrawFailed = false;

    public ScenarioController(Scenario scenario) {
        this.scenario = scenario;
    }

    public boolean simulate(OperationType type, String account, double value) {
        switch (scenario) {
            case P1A:
                // tudo ok
                return true;

            case P1B:
                // saque OK, depósito falha
                if (type == OperationType.DEPOSIT && account.equals("ND87632")) {
                    return false;
                }
                return true;

            case P2A:
                // 5 withdraws OK
                return true;

            case P2B:
                // primeiro 3 withdraws ok, a partir do 4o falha
                if (type == OperationType.WITHDRAW && account.equals("BC3456A")) {
                    int attempts = counters.computeIfAbsent(account, k -> new AtomicInteger(0)).incrementAndGet();
                    return attempts <= 3;
                }
                return true; // outras operações ok

            case P3A:
                return true; // ambos os withdraws ok

            case P3B:
                // primeiro withdraw falha, segundo succeed (ou vice-versa)
                if (type == OperationType.WITHDRAW) {
                    if (account.equals("AG7340H") && !p3bFirstWithdrawFailed) {
                        p3bFirstWithdrawFailed = true;
                        return false; // primeiro falha
                    } else if (account.equals("TG23986Q") && p3bFirstWithdrawFailed) {
                        return true; // segundo succeed
                    } else if (account.equals("TG23986Q") && !p3bFirstWithdrawFailed) {
                        p3bFirstWithdrawFailed = true;
                        return false; // primeiro falha, segundo também?
                    } else if (account.equals("AG7340H") && p3bFirstWithdrawFailed) {
                        return true; // segundo succeed
                    }
                }
                return true;

            default:
                return true;
        }
    }

    public void reset() {
        counters.clear();
        p1bDepositFailed = false;
        p3bFirstWithdrawFailed = false;
    }
}