package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.Properties;

@SpringBootApplication
public class DemoApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoApplication.class);

    public static void main(String[] args) {
        LOGGER.debug("11111111");

        SpringApplication.run(DemoApplication.class, args);

        LOGGER.debug("22222222");

    }


    static class EmailAddress {
        private InternetAddress address;

        public EmailAddress(String address) {
            try {
                this.address = new InternetAddress(address, true);
            } catch (AddressException e) {
                e.printStackTrace();
            }
        }
    }

    @Bean
    TransportFactory transportFactory() {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.debug", "false");
        return new TransportFactory(props, "smtp", "127.0.0.1", 3025);
    }

    @Bean
    TransportPool transportPool(TransportFactory factory) {
        return new TransportPool(factory);
    }

    @Bean
    public JavaMailSender javaMailSender(TransportPool pool) {
        return new PooledJavaMailSender(pool);
    }

}
