package br.university.project.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class MetricsAnalyzer {

    public static void main(String[] args) throws IOException {
        File resultsDir = new File("results");
        Map<String, Object> finalReport = new LinkedHashMap<>();

        // Coletar todos os arquivos agregados
        for (File file : resultsDir.listFiles((dir, name) -> name.startsWith("aggregated-") && name.endsWith(".json"))) {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> data = mapper.readValue(file, Map.class);

            String filename = file.getName().replace("aggregated-", "").replace(".json", "");
            finalReport.put(filename, data);
        }

        // Gerar relatório final
        File finalReportFile = new File("results/final-experiment-report.json");
        try (FileWriter fw = new FileWriter(finalReportFile)) {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(fw, finalReport);
        }

        System.out.println("Final report generated: " + finalReportFile.getAbsolutePath());
        printSummary(finalReport);
    }

    private static void printSummary(Map<String, Object> finalReport) {
        System.out.println("\n=== EXPERIMENT SUMMARY ===");

        for (Map.Entry<String, Object> entry : finalReport.entrySet()) {
            String config = entry.getKey();
            Map<?, ?> data = (Map<?, ?>) entry.getValue();

            double correctness = (Double) data.get("correctnessRatio");
            boolean consistent = (Boolean) data.get("consistent");
            Map<?, ?> toolUsage = (Map<?, ?>) data.get("toolUsage");

            System.out.printf("Config: %s%n", config);
            System.out.printf("  Correctness: %.2f%%%n", correctness * 100);
            System.out.printf("  Consistent: %s%n", consistent);
            System.out.printf("  Tool Usage: %s%n", toolUsage);
            System.out.println();
        }
    }
}