package br.university.project.runner;

import br.university.project.tools.BankToolsA;
import br.university.project.tools.BankToolsB;
import br.university.project.util.CallLogger;

import java.io.File;
import java.util.UUID;

/**
 * MainRunner: exemplo que instancia os tools, configura o ScenarioController e grava logs.
 * Aqui também é o local onde você deverá integrar com LangChain4J para registrar as tools
 * e disparar o prompt para o LLM (Ollama).
 */
public class MainRunner {

    public static void main(String[] args) throws Exception {
        // Parâmetros de exemplo: use args ou um arquivo de configuração para escolher conf/prompt/scenario
        String conf = "CONF1"; // CONF1 | CONF2 | CONF3 | CONF4
        String prompt = "Transfer 1000 from account BC12345 to the account ND87632 by withdrawing from the first and depositing into the second...";
        ScenarioController.Scenario scenario = ScenarioController.Scenario.P1A;

        String runId = UUID.randomUUID().toString();
        ScenarioController sc = new ScenarioController(scenario);
        CallLogger logger = new CallLogger();

        // Crie instâncias das tools conforme configuração
        BankToolsA a = new BankToolsA(sc, logger, runId);
        BankToolsB b = new BankToolsB(sc, logger, runId);

        // --- INTEGRAÇÃO COM LANGCHAIN4J (exemplo conceitual) ---
        // Aqui você precisa criar o agente LangChain4J e registrar as ferramentas.
        // Exemplo conceitual (APIs reais podem variar; consulte docs):
        //
        // Agent agent = Agent.builder()
        //       .llm( OllamaClient.withModel("mistral:latest") )
        //       .tool(a)   // registra métodos anotados com @Tool
        //       .tool(b)   // registre conforme a configuração CONFx e a ordem desejada
        //       .build();
        //
        // AgentResponse response = agent.run(prompt);
        // -- Salve response.getText() junto aos logs --
        //
        // Referências: LangChain4J documenta que você expõe methods com @Tool e LangChain4J introspeciona as classes. :contentReference[oaicite:2]{index=2}

        // Para fins de teste sem agente: você pode simular chamadas manualmente para verificar logging:
        boolean w = a.withdraw("BC12345", 1000.0);
        boolean d = a.deposit("ND87632", 1000.0);
        if (w && d) {
            a.taxes("BC12345", 1.50);
        } else if (w && !d) {
            a.returnValue("BC12345", 1000.0);
        }

        // grava logs no disco
        File out = new File("results/run-" + runId + ".json");
        out.getParentFile().mkdirs();
        logger.dumpJson(out);
        System.out.println("Run saved to " + out.getAbsolutePath());
    }
}