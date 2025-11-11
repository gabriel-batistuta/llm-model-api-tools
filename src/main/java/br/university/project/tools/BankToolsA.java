package br.university.project.tools;

import br.university.project.runner.ScenarioController;
import br.university.project.util.CallLogger;
//import dev.langchain4j.agent.tool.annotation.Tool;
//import dev.langchain4j.agent.tool.annotation.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;


import java.util.Map;

public class BankToolsA {

    private final ScenarioController scenario;
    private final CallLogger logger;
    private final String runId;

    public BankToolsA(ScenarioController scenario, CallLogger logger, String runId) {
        this.scenario = scenario;
        this.logger = logger;
        this.runId = runId;
    }

    @Tool("Withdraw a value from an account and return if the operation was successful or not")
    public boolean withdraw(@P("account number") String accountNumber, @P("value to be withdraw") double value) {
        boolean success = scenario.simulate(br.university.project.model.OperationType.WITHDRAW, accountNumber, value);
        logger.log(runId, "BankToolsA", "withdraw", Map.of("account", accountNumber, "value", value), success);
        return success;
    }

    @Tool("Deposit the value into an account and return if the operation was successful or not")
    public boolean deposit(@P("account number") String accountNumber, @P("value to be deposited") double value) {
        boolean success = scenario.simulate(br.university.project.model.OperationType.DEPOSIT, accountNumber, value);
        logger.log(runId, "BankToolsA", "deposit", Map.of("account", accountNumber, "value", value), success);
        return success;
    }

    @Tool("Perform a payment with a value using the money from an account and return if the operation was successful or not")
    public boolean payment(@P("account number") String accountNumber, @P("value of the payment") double value) {
        boolean success = scenario.simulate(br.university.project.model.OperationType.PAYMENT, accountNumber, value);
        logger.log(runId, "BankToolsA", "payment", Map.of("account", accountNumber, "value", value), success);
        return success;
    }

    @Tool("Charge the value of a tax from the account and return if the operation was successful or not")
    public boolean taxes(@P("account number") String accountNumber, @P("value of the tax") double value) {
        boolean success = scenario.simulate(br.university.project.model.OperationType.TAX, accountNumber, value);
        logger.log(runId, "BankToolsA", "taxes", Map.of("account", accountNumber, "value", value), success);
        return success;
    }

    @Tool("Return a value of a failed operation to an account and return if the operation was successful or not")
    public boolean returnValue(@P("account number") String accountNumber, @P("value to be returned") double value) {
        boolean success = scenario.simulate(br.university.project.model.OperationType.RETURN, accountNumber, value);
        logger.log(runId, "BankToolsA", "returnValue", Map.of("account", accountNumber, "value", value), success);
        return success;
    }
}
