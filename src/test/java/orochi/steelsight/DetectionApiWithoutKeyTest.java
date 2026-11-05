package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With no key configured the API refuses everyone, including a caller sending an empty key. */
@SpringBootTest(properties = {"steelsight.storage.dir=target/test-uploads", "steelsight.detection.api-key="})
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class DetectionApiWithoutKeyTest {

    @Autowired MockMvc mvc;

    @Test
    void everyCallIsRefused() throws Exception {
        for (String key : new String[]{"", "anything"}) {
            mvc.perform(post("/api/v1/detection/alerts").header("X-Api-Key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
