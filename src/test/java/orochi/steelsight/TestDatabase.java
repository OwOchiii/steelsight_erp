package orochi.steelsight;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One real PostgreSQL with pgvector, started once and shared by every test class. */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

    static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        CONTAINER.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return CONTAINER;
    }
}
