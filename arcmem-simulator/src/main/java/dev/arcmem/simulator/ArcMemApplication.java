package dev.arcmem.simulator;

import com.embabel.dice.web.rest.DiceRestConfiguration;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication(scanBasePackages = {
        "dev.arcmem.core",
        "dev.arcmem.simulator"
})
@EnableDrivine
@EnableDrivinePropertiesConfig
@Import(DiceRestConfiguration.class)
@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
@Theme("arc-retro")
public class ArcMemApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        var application = new SpringApplication(ArcMemApplication.class);
        configureVaadinProjectPaths().ifPresent(properties -> {
            properties.forEach((key, value) -> System.setProperty(key, value.toString()));
            application.setDefaultProperties(properties);
        });
        application.run(args);
    }

    private static Optional<Map<String, Object>> configureVaadinProjectPaths() {
        if (System.getProperty("project.basedir") != null
            && System.getProperty("vaadin.frontend.frontend.folder") != null) {
            return Optional.empty();
        }

        var workingDirectory = Path.of("").toAbsolutePath().normalize();
        var moduleDirectory = Files.isDirectory(workingDirectory.resolve("frontend"))
                ? workingDirectory
                : workingDirectory.resolve("arcmem-simulator");
        var frontendDirectory = moduleDirectory.resolve("frontend");

        if (Files.isDirectory(frontendDirectory)) {
            return Optional.of(Map.of(
                    "project.basedir", moduleDirectory.toString(),
                    "vaadin.frontend.frontend.folder", frontendDirectory.toString(),
                    "vaadin.frontend.generated.folder", frontendDirectory.resolve("generated").toString()
            ));
        }

        return Optional.empty();
    }
}
