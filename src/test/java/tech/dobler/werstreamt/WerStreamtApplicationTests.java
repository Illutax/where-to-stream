package tech.dobler.werstreamt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
//@TestPropertySource(properties = "wer-streamt.path=/src/test/resources/test-assets")
class WerStreamtApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

}
