package dev.dunnam.diceanchors.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptPathConstants")
class PromptPathConstantsTest {

    private List<Field> constantFields() {
        return Arrays.stream(PromptPathConstants.class.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers())
                        && Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && f.getType() == String.class)
                .toList();
    }

    @Test
    @DisplayName("all constants are non-null and non-empty")
    void allConstants_nonNullAndNonEmpty() throws IllegalAccessException {
        var fields = constantFields();
        assertThat(fields).isNotEmpty();
        for (var field : fields) {
            var value = (String) field.get(null);
            assertThat(value)
                    .as("constant %s must not be null", field.getName())
                    .isNotNull();
            assertThat(value)
                    .as("constant %s must not be empty", field.getName())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("all constants end with .jinja")
    void allConstants_endWithJinja() throws IllegalAccessException {
        for (var field : constantFields()) {
            var value = (String) field.get(null);
            assertThat(value)
                    .as("constant %s ('%s') must end with .jinja", field.getName(), value)
                    .endsWith(".jinja");
        }
    }

    @Test
    @DisplayName("all constants resolve to an existing classpath resource")
    void allConstants_existAsClasspathResource() throws IllegalAccessException {
        var loader = getClass().getClassLoader();
        for (var field : constantFields()) {
            var path = (String) field.get(null);
            try (var stream = loader.getResourceAsStream(path)) {
                assertThat(stream)
                        .as("constant %s points to missing resource '%s'", field.getName(), path)
                        .isNotNull();
            } catch (java.io.IOException e) {
                // stream.close() threw — resource still existed, treat as pass
            }
        }
    }
}
