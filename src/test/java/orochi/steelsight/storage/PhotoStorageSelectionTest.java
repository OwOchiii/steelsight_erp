package orochi.steelsight.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoStorageSelectionTest {

    @Configuration(proxyBeanMethods = false)
    static class Client {
        @Bean
        RestClient.Builder builder() {
            return RestClient.builder();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Client.class, LocalPhotoStorage.class, SupabasePhotoStorage.class)
            .withPropertyValues("steelsight.storage.dir=target/selection-test");

    @Test
    void usesLocalDiskByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PhotoStorage.class);
            assertThat(context).hasSingleBean(LocalPhotoStorage.class);
        });
    }

    @Test
    void usesSupabaseWhenAskedTo() {
        runner.withPropertyValues("steelsight.storage.type=supabase",
                        "steelsight.storage.supabase-url=https://example.supabase.co",
                        "steelsight.storage.supabase-key=secret-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(PhotoStorage.class);
                    assertThat(context).hasSingleBean(SupabasePhotoStorage.class);
                });
    }

    @Test
    void refusesToStartOnSupabaseWithoutItsSettings() {
        runner.withPropertyValues("steelsight.storage.type=supabase")
                .run(context -> assertThat(context).hasFailed());
    }
}
