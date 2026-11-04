package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs db/schema_checks.sql with psql against the schema Flyway built. The script rolls itself back. */
@SpringBootTest
@Import(TestDatabase.class)
class SchemaChecksSqlTest {

    // Autowiring the DataSource makes sure the context, and so the Flyway migration, has run first
    @Autowired
    DataSource dataSource;

    @Test
    void schemaRulesHold() throws Exception {
        TestDatabase.CONTAINER.copyFileToContainer(
                MountableFile.forClasspathResource("db/schema_checks.sql"), "/tmp/schema_checks.sql");

        ExecResult result = TestDatabase.CONTAINER.execInContainer(
                "psql", "-U", TestDatabase.CONTAINER.getUsername(), "-d", TestDatabase.CONTAINER.getDatabaseName(),
                "-f", "/tmp/schema_checks.sql");

        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        assertThat(result.getStderr()).contains("ALL CHECKS PASSED");
    }
}
