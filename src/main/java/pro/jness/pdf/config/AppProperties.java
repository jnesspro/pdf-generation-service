package pro.jness.pdf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import javax.annotation.PostConstruct;

/**
 * @author Aleksandr Streltsov (jness.pro@gmail.com)
 *         on 24/08/16
 */
@Configuration
@PropertySources(@PropertySource("classpath:application.properties"))
public class AppProperties {

    @Value("${application.version}")
    private String version;
    @Value("${application.build_info}")
    private String buildInfo;
    @Value("${tasks_directory}")
    private String tasksDirectory;

    @PostConstruct
    public void init() {
    }

    public String getVersion() {
        return version;
    }

    public String getBuildInfo() {
        return buildInfo;
    }

    public String getTasksDirectory() {
        return tasksDirectory;
    }
}
