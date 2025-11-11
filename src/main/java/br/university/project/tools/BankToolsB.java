package br.university.project.tools;

import br.university.project.model.OperationType;
import br.university.project.runner.ScenarioController;
import br.university.project.util.CallLogger;
//import dev.langchain4j.agent.tool.annotation.Tool;
//import dev.langchain4j.agent.tool.annotation.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.util.Map;

public class BankToolsB {

    private final ScenarioController scenario;
    private final CallLogger logger;
    private final String runId;

    public BankToolsB(ScenarioController scenario, CallLogger logger, String runId) {
        this.scenario = scenario;
        this.logger = logger;
        this.runId = runId;
    }

    @Tool("Execute an operation in an account with a given value and return if the operation was successful or not")
    public boolean executeOperation(@P("WITHDRAW if ... DEPOSIT if ... TAX ... RETURN ... PAYMENT ...") OperationType type,
                                    @P("account number") String accountNumber,
                                    @P("value to be used in the operation") double value) {
        boolean success = scenario.simulate(type, accountNumber, value);
        logger.log(runId, "BankToolsB", "executeOperation", Map.of("type", type, "account", accountNumber, "value", value), success);
        return success;
    }
}
