package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestDatabase.class)
class SteelsightErpApplicationTests {

    @Test
    void contextLoads() {
    }

}
