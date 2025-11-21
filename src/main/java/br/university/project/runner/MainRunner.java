package br.university.project.runner;

import br.university.project.model.OperationType;
import br.university.project.tools.BankToolsA;
import br.university.project.tools.BankToolsB;
import br.university.project.util.CallLogger;
import dev.langchain4j.service.AiServices;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.*;

public class MainRunner {

    private static final String PROMPT1 = "Transfer 1000 from account BC12345 to the account ND87632 by withdrawing from the first and depositing into the second. If both operations are successful, change 1.50 from the first account. If not, return the value to the account and don't charge the tax.";
    private static final String PROMPT2 = "Execute withdrawal operations of 500 from account BC3456A one at a time. Repeat until a failure is received, or until this operation has been executed 5 times. Deposit the total value withdrawn in account FG62495S and pay a tax of 10% of the value deposited in the account FG62495S.";
    private static final String PROMPT3 = "Withdraw 600 from account AG7340H and 700 from account TG23986Q. If one of the operations is not successful, return the value to the other account and don't execute anything else. If both operations are successful, perform a deposit of the summed value into account WS2754T and perform a payment of 1200 in this same account.";

    private static final String[] CONFIGS = new String[]{"CONF1", "CONF2", "CONF3", "CONF4"};
    private static final int RUNS_PER_COMBINATION = 10;
    private static final long OLLAMA_TIMEOUT_SECONDS = 300;

    interface BankingAssistant {
        String chat(String userMessage);
    }

    public static void main(String[] args) throws Exception {
        String startFromConfig = getStartConfigFromArgs(args);
        String startFromPrompt = getStartPromptFromArgs(args);
        String startFromScenario = getStartScenarioFromArgs(args);

        System.out.println("🚀 Iniciando execução a partir de: " +
                (startFromConfig != null ? startFromConfig : "INÍCIO") + " " +
                (startFromPrompt != null ? startFromPrompt : "") + " " +
                (startFromScenario != null ? startFromScenario : ""));

        boolean ollamaAvailable = checkOllamaAvailability();
        System.out.println("Ollama available: " + ollamaAvailable);

        if (!ollamaAvailable) {
            System.err.println("ERROR: Ollama is not available. Cannot run experiments without LLM.");
            System.exit(1);
        }

        Map<String, List<String>> acceptance = defineAcceptanceCriteria();
        saveAcceptanceToFile(acceptance, new File("results/acceptance_criteria.txt"));

        Map<String, String> prompts = Map.of(
                "P1", PROMPT1,
                "P2", PROMPT2,
                "P3", PROMPT3
        );

        boolean shouldStart = (startFromConfig == null); 

        for (String conf : CONFIGS) {
            if (!shouldStart) {
                if (conf.equals(startFromConfig)) {
                    shouldStart = true;
                    System.out.println("✅ Iniciando a partir da configuração: " + conf);
                } else {
                    System.out.println("⏭️  Pulando configuração: " + conf);
                    continue;
                }
            }

            for (String pKey : prompts.keySet()) {
                if (startFromPrompt != null && shouldStart) {
                    if (!pKey.equals(startFromPrompt)) {
                        System.out.println("⏭️  Pulando prompt: " + pKey);
                        continue;
                    } else {
                        startFromPrompt = null;
                    }
                }

                for (String scenarioSuffix : new String[]{"A", "B"}) {
                    if (startFromScenario != null && shouldStart) {
                        if (!scenarioSuffix.equals(startFromScenario)) {
                            System.out.println("⏭️  Pulando cenário: " + scenarioSuffix);
                            continue;
                        } else {
                            startFromScenario = null;
                        }
                    }

                    ScenarioController.Scenario scEnum = mapToScenario(pKey, scenarioSuffix);
                    System.out.printf("=== Running config=%s prompt=%s scenario=%s ===%n", conf, pKey, scenarioSuffix);
                    List<Map<String, Object>> runSummaries = new ArrayList<>();

                    for (int runIdx = 0; runIdx < RUNS_PER_COMBINATION; runIdx++) {
                        String runId = UUID.randomUUID().toString();
                        ScenarioController scenarioController = new ScenarioController(scEnum);
                        CallLogger logger = new CallLogger();

                        BankToolsA a = new BankToolsA(scenarioController, logger, runId);
                        BankToolsB b = new BankToolsB(scenarioController, logger, runId);

                        String llmResponseText;
                        boolean usedLlm = false;

                        try {
                            llmResponseText = callWithTools(prompts.get(pKey), conf, a, b);
                            usedLlm = true;
                        } catch (Exception e) {
                            System.err.println("ERROR in LLM call: " + e.getMessage());
                            llmResponseText = "[ERROR] " + e.getMessage();
                            throw new RuntimeException("LLM call failed for config " + conf + " prompt " + pKey + " scenario " + scenarioSuffix, e);
                        }

                        File out = new File("results/run-" + runId + ".json");
                        out.getParentFile().mkdirs();
                        logger.dumpJson(out);

                        Set<String> toolsUsed = new LinkedHashSet<>();
                        for (Map<String, Object> ev : logger.getEvents()) {
                            Object tc = ev.get("toolClass");
                            if (tc != null) toolsUsed.add(tc.toString());
                        }

                        if (toolsUsed.isEmpty()) {
                            System.err.println("WARNING: LLM did not use any tools for run " + runId);
                            toolsUsed.add("NO_TOOLS_USED");
                        }

                        List<String> expectedOps = acceptance.get(pKey + scenarioSuffix);
                        EvaluationResult eval = evaluateRun(logger.getEvents(), expectedOps);

                        Map<String, Object> runSummary = new LinkedHashMap<>();
                        runSummary.put("runId", runId);
                        runSummary.put("config", conf);
                        runSummary.put("prompt", pKey);
                        runSummary.put("scenario", scenarioSuffix);
                        runSummary.put("usedLlm", usedLlm);
                        runSummary.put("llmResponseText", llmResponseText);
                        runSummary.put("toolsUsed", new ArrayList<>(toolsUsed));
                        runSummary.put("events", logger.getEvents());
                        runSummary.put("evaluation", eval.toMap());
                        runSummary.put("eventsCount", logger.getEvents().size());
                        runSummaries.add(runSummary);

                        File summaryOut = new File(String.format("results/summary-%s-%s-%s-run%d.json", conf, pKey, scenarioSuffix, runIdx + 1));
                        try (FileWriter fw = new FileWriter(summaryOut)) {
                            fw.write(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(runSummary));
                        } catch (IOException ioe) {
                            System.err.println("Could not write summary file: " + ioe.getMessage());
                        }

                        System.out.printf("Run %d/%d completed - Tools used: %s, Correct: %s%n",
                                runIdx + 1, RUNS_PER_COMBINATION, toolsUsed, eval.correct);

                        // pausa entre execuções para não sobrecarregar o Ollama
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } // runs

                    AggregatedMetrics aggregated = aggregateMetrics(runSummaries);
                    File aggOut = new File(String.format("results/aggregated-%s-%s-%s.json", conf, pKey, scenarioSuffix));
                    try (FileWriter fw = new FileWriter(aggOut)) {
                        fw.write(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(aggregated.toMap()));
                        System.out.printf("Aggregated metrics saved to %s%n", aggOut.getAbsolutePath());
                    } catch (IOException ioe) {
                        System.err.println("Could not write aggregated file: " + ioe.getMessage());
                    }
                } // scenario
            } // prompt
        } // conf

        System.out.println("All experiments finished. Check results/ for logs and summaries.");
    }

    private static String getStartConfigFromArgs(String[] args) {
        if (args.length > 0) {
            String config = args[0].toUpperCase();
            if (Arrays.asList(CONFIGS).contains(config)) {
                return config;
            } else {
                System.err.println("⚠️  Configuração inválida: " + config + ". Usando: " + Arrays.toString(CONFIGS));
            }
        }
        return null;
    }

    private static String getStartPromptFromArgs(String[] args) {
        if (args.length > 1) {
            String prompt = args[1].toUpperCase();
            if (prompt.matches("P[1-3]")) {
                return prompt;
            } else {
                System.err.println("⚠️  Prompt inválido: " + prompt + ". Usando: P1, P2, P3");
            }
        }
        return null;
    }

    private static String getStartScenarioFromArgs(String[] args) {
        if (args.length > 2) {
            String scenario = args[2].toUpperCase();
            if (scenario.equals("A") || scenario.equals("B")) {
                return scenario;
            } else {
                System.err.println("⚠️  Cenário inválido: " + scenario + ". Usando: A, B");
            }
        }
        return null;
    }

    private static String callWithTools(String prompt, String config, BankToolsA toolsA, BankToolsB toolsB) {
        final String baseUrl = System.getenv("OLLAMA_BASE_URL");
        final String actualBaseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:11434" : baseUrl;
        final String finalPrompt = prompt;
        final String finalConfig = config;
        final BankToolsA finalToolsA = toolsA;
        final BankToolsB finalToolsB = toolsB;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            String[] modelNames = {"mistral:latest", "llama3.1:latest", "llama3:latest"};
            int maxRetriesPerModel = 2;
            Exception lastException = null;

            for (String modelName : modelNames) {
                for (int retry = 0; retry < maxRetriesPerModel; retry++) {
                    try {
                        System.out.println("🔄 Tentativa " + (retry + 1) + " com modelo: " + modelName);

                        dev.langchain4j.model.ollama.OllamaChatModel model =
                                dev.langchain4j.model.ollama.OllamaChatModel.builder()
                                        .baseUrl(actualBaseUrl)
                                        .modelName(modelName)
                                        .timeout(java.time.Duration.ofSeconds(300))
                                        .temperature(0.0)
                                        .build();

                        BankingAssistant assistant = buildAssistant(model, finalConfig, finalToolsA, finalToolsB);

                        System.out.println("Calling LLM with prompt: " + finalPrompt.substring(0, Math.min(100, finalPrompt.length())) + "...");
                        String result = assistant.chat(finalPrompt);
                        System.out.println("✅ LLM response received with model: " + modelName);
                        return result;

                    } catch (Exception e) {
                        lastException = e;
                        System.err.println("❌ Tentativa " + (retry + 1) + " com modelo " + modelName + " falhou: " + e.getMessage());

                        if (e.getCause() instanceof IllegalArgumentException) {
                            System.err.println("🔍 Erro de parâmetros inválidos - o modelo está gerando argumentos incorretos");
                        }

                        if (retry < maxRetriesPerModel - 1) {
                            try {
                                System.out.println("⏳ Aguardando 3 segundos antes da próxima tentativa...");
                                Thread.sleep(3000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(ie);
                            }
                        }
                    }
                }

                System.out.println("🔁 Mudando para próximo modelo...");
            }

            throw new RuntimeException("❌ Todos os modelos e tentativas falharam. Último erro: " +
                    (lastException != null ? lastException.getMessage() : "Desconhecido"));
        });

        try {
            return future.get(OLLAMA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Model response timed out after " + OLLAMA_TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static BankingAssistant buildAssistant(dev.langchain4j.model.ollama.OllamaChatModel model,
                                                   String config, BankToolsA toolsA, BankToolsB toolsB) {
        switch (config) {
            case "CONF1":
                return AiServices.builder(BankingAssistant.class)
                        .chatLanguageModel(model)
                        .tools(toolsA)
                        .build();
            case "CONF2":
                return AiServices.builder(BankingAssistant.class)
                        .chatLanguageModel(model)
                        .tools(toolsB)
                        .build();
            case "CONF3":
                return AiServices.builder(BankingAssistant.class)
                        .chatLanguageModel(model)
                        .tools(toolsA, toolsB)
                        .build();
            case "CONF4":
                return AiServices.builder(BankingAssistant.class)
                        .chatLanguageModel(model)
                        .tools(toolsB, toolsA)
                        .build();
            default:
                throw new IllegalArgumentException("Unknown configuration: " + config);
        }
    }

    private static boolean checkOllamaAvailability() {
        try {
            String baseUrl = System.getenv("OLLAMA_BASE_URL");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:11434";
            }

            java.net.URL url = new java.net.URL(baseUrl + "/api/tags");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            System.err.println("Ollama not available: " + e.getMessage());
            return false;
        }
    }

    // -----------------------------
    // Acceptance criteria
    // -----------------------------
    private static Map<String, List<String>> defineAcceptanceCriteria() {
        Map<String, List<String>> acceptance = new LinkedHashMap<>();

        acceptance.put("P1A", List.of(
                "withdraw(BC12345,1000.0)",
                "deposit(ND87632,1000.0)",
                "taxes(BC12345,1.5)"
        ));
        acceptance.put("P1B", List.of(
                "withdraw(BC12345,1000.0)",
                "deposit(ND87632,1000.0)->FAILED",
                "returnValue(BC12345,1000.0)"
        ));

        acceptance.put("P2A", List.of(
                "withdraw(BC3456A,500.0)",
                "withdraw(BC3456A,500.0)",
                "withdraw(BC3456A,500.0)",
                "withdraw(BC3456A,500.0)",
                "withdraw(BC3456A,500.0)",
                "deposit(FG62495S,2500.0)",
                "taxes(FG62495S,250.0)"
        ));
        acceptance.put("P2B", List.of(
                "withdraw(BC3456A,500.0)",
                "withdraw(BC3456A,500.0)",
                "withdraw(BC3456A,500.0)",
                "deposit(FG62495S,1500.0)",
                "taxes(FG62495S,150.0)"
        ));

        acceptance.put("P3A", List.of(
                "withdraw(AG7340H,600.0)",
                "withdraw(TG23986Q,700.0)",
                "deposit(WS2754T,1300.0)",
                "payment(WS2754T,1200.0)"
        ));
        acceptance.put("P3B", List.of(
                "withdraw(AG7340H,600.0) OR withdraw(TG23986Q,700.0)->FAILED",
                "returnValue(<the one that succeeded>, <value>)"
        ));

        return acceptance;
    }

    private static void saveAcceptanceToFile(Map<String, List<String>> acceptance, File out) {
        out.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("Acceptance criteria (prompt+scenario -> expected operations):\n\n");
            for (Map.Entry<String, List<String>> e : acceptance.entrySet()) {
                fw.write(e.getKey() + ":\n");
                for (String s : e.getValue()) fw.write("  - " + s + "\n");
                fw.write("\n");
            }
            System.out.println("Acceptance criteria saved to " + out.getAbsolutePath());
        } catch (IOException ex) {
            System.err.println("Could not save acceptance criteria: " + ex.getMessage());
        }
    }

    private static ScenarioController.Scenario mapToScenario(String pKey, String suffix) {
        switch (pKey + suffix) {
            case "P1A":
                return ScenarioController.Scenario.P1A;
            case "P1B":
                return ScenarioController.Scenario.P1B;
            case "P2A":
                return ScenarioController.Scenario.P2A;
            case "P2B":
                return ScenarioController.Scenario.P2B;
            case "P3A":
                return ScenarioController.Scenario.P3A;
            case "P3B":
                return ScenarioController.Scenario.P3B;
            default:
                return ScenarioController.Scenario.P1A;
        }
    }

    private static EvaluationResult evaluateRun(List<Map<String, Object>> events, List<String> expectedOps) {
        List<String> ops = new ArrayList<>();
        for (Map<String, Object> e : events) {
            String method = (String) e.get("method");
            Map<?, ?> params = (Map<?, ?>) e.get("params");
            boolean result = Boolean.TRUE.equals(e.get("result"));

            String op;
            if ("executeOperation".equals(method)) {
                Object typeObj = params.get("type");
                String typeName = typeObj == null ? "" : typeObj.toString();
                String mapped = mapOperationTypeToMethod(typeName);
                Object account = params.get("account");
                Object value = params.get("value");
                op = String.format("%s(%s,%s)", mapped, account, value);
            } else {
                Object account = params.get("account");
                Object value = params.get("value");
                op = String.format("%s(%s,%s)", method, account, value);
            }
            if (!result) op += "->FAILED";
            ops.add(op);
        }

        List<String> mismatches = new ArrayList<>();
        int idx = 0;
        for (String expected : expectedOps) {
            boolean matched = false;
            for (int j = idx; j < ops.size(); j++) {
                String observed = ops.get(j);
                if (expected.contains(" OR ")) {
                    String[] options = expected.split(" OR ");
                    for (String opt : options) {
                        if (normalize(opt).equals(normalize(observed))) {
                            matched = true;
                            idx = j + 1;
                            break;
                        }
                    }
                    if (matched) break;
                } else if (expected.contains("<")) {
                    String expectedMethod = expected.split("\\(")[0];
                    if (observed.startsWith(expectedMethod + "(")) {
                        matched = true;
                        idx = j + 1;
                        break;
                    }
                } else {
                    if (normalize(expected).equals(normalize(observed))) {
                        matched = true;
                        idx = j + 1;
                        break;
                    }
                }
            }
            if (!matched) mismatches.add("Expected not found: " + expected);
        }

        boolean correct = mismatches.isEmpty();
        return new EvaluationResult(correct, mismatches, ops, expectedOps);
    }

    private static String mapOperationTypeToMethod(String typeName) {
        if (typeName == null) return "executeOperation";
        switch (typeName.toUpperCase(Locale.ROOT)) {
            case "WITHDRAW":
                return "withdraw";
            case "DEPOSIT":
                return "deposit";
            case "TAX":
                return "taxes";
            case "RETURN":
                return "returnValue";
            case "PAYMENT":
                return "payment";
            default:
                return "executeOperation";
        }
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").replaceAll(",0\\)", "\\)").trim();
    }

    private static AggregatedMetrics aggregateMetrics(List<Map<String, Object>> runSummaries) {
        int total = runSummaries.size();
        int correctCount = 0;
        Map<String, Integer> toolUsage = new HashMap<>();
        Set<String> distinctResults = new HashSet<>();

        for (Map<String, Object> rs : runSummaries) {
            Map<?, ?> eval = (Map<?, ?>) rs.get("evaluation");
            Boolean corr = (Boolean) eval.get("correct");
            if (corr != null && corr) correctCount++;

            Object toolsUsedObj = rs.get("toolsUsed");
            if (toolsUsedObj instanceof List) {
                List<?> lst = (List<?>) toolsUsedObj;
                for (Object t : lst) {
                    if (t != null) {
                        String tn = t.toString();
                        toolUsage.merge(tn, 1, Integer::sum);
                    }
                }
            }

            distinctResults.add(eval.toString());
        }

        double correctness = total == 0 ? 0.0 : (double) correctCount / total;
        boolean consistent = distinctResults.size() == 1;

        return new AggregatedMetrics(total, correctCount, correctness, consistent, toolUsage);
    }

    private static class EvaluationResult {
        final boolean correct;
        final List<String> mismatches;
        final List<String> observedOps;
        final int expectedCount;
        final int observedCount;
        final double sequenceAccuracy;

        EvaluationResult(boolean correct, List<String> mismatches, List<String> observedOps, List<String> expectedOps) {
            this.correct = correct;
            this.mismatches = mismatches;
            this.observedOps = observedOps;
            this.expectedCount = expectedOps.size();
            this.observedCount = observedOps.size();

            int matches = 0;
            int minSize = Math.min(expectedOps.size(), observedOps.size());
            for (int i = 0; i < minSize; i++) {
                if (normalize(expectedOps.get(i)).equals(normalize(observedOps.get(i)))) {
                    matches++;
                }
            }
            this.sequenceAccuracy = minSize == 0 ? 0.0 : (double) matches / minSize;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("correct", correct);
            m.put("mismatches", mismatches);
            m.put("observedOps", observedOps);
            m.put("expectedCount", expectedCount);
            m.put("observedCount", observedCount);
            m.put("sequenceAccuracy", sequenceAccuracy);
            return m;
        }
    }

    private static class AggregatedMetrics {
        final int totalRuns;
        final int correctRuns;
        final double correctnessRatio;
        final boolean consistent;
        final Map<String, Integer> toolUsage;

        AggregatedMetrics(int totalRuns, int correctRuns, double correctnessRatio, boolean consistent, Map<String, Integer> toolUsage) {
            this.totalRuns = totalRuns;
            this.correctRuns = correctRuns;
            this.correctnessRatio = correctnessRatio;
            this.consistent = consistent;
            this.toolUsage = toolUsage;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalRuns", totalRuns);
            m.put("correctRuns", correctRuns);
            m.put("correctnessRatio", correctnessRatio);
            m.put("consistent", consistent);
            m.put("toolUsage", toolUsage);
            return m;
        }
    }
}