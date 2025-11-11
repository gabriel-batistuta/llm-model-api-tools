package br.university.project.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CallLogger {
    private final List<Map<String,Object>> events = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger seq = new AtomicInteger(0);

    public void log(String runId, String toolClass, String method, Map<String,Object> params, boolean result) {
        Map<String,Object> e = new LinkedHashMap<>();
        e.put("runId", runId);
        e.put("sequence", seq.incrementAndGet());
        e.put("timestamp", Instant.now().toString());
        e.put("toolClass", toolClass);
        e.put("method", method);
        e.put("params", params);
        e.put("result", result);
        events.add(e);
    }

    public List<Map<String,Object>> getEvents() { return events; }

    public void dumpJson(File out) throws IOException {
        ObjectMapper m = new ObjectMapper();
        m.writerWithDefaultPrettyPrinter().writeValue(out, events);
    }

    public void dumpCsv(File out) throws IOException {
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("runId,sequence,timestamp,toolClass,method,params,result\n");
            for (Map<String,Object> e : events) {
                fw.write(String.format("\"%s\",%s,%s,%s,%s,\"%s\"\n",
                        e.get("runId"),
                        e.get("sequence"),
                        e.get("timestamp"),
                        e.get("toolClass"),
                        e.get("method"),
                        e.get("result")
                ));
            }
        }
    }
}
