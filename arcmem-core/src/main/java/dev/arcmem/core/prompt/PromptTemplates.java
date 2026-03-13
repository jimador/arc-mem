package dev.arcmem.core.prompt;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import com.hubspot.jinjava.Jinjava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PromptTemplates {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplates.class);
    private static final Jinjava JINJAVA = new Jinjava();
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptTemplates() {
    }

    public static String load(String classpath) {
        return CACHE.computeIfAbsent(classpath, PromptTemplates::readClasspathResource);
    }

    public static String render(String classpath, Map<String, ?> variables) {
        var template = load(classpath);
        return JINJAVA.render(template, variables != null ? variables : Map.of());
    }

    private static String readClasspathResource(String classpath) {
        try {
            var resource = new ClassPathResource(classpath);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to load prompt template {}: {}", classpath, e.getMessage());
            return "";
        }
    }
}
