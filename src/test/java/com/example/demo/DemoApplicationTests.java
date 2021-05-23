package com.example.demo;

import com.icegreen.greenmail.imap.ImapHostManager;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.internet.AddressException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.demo.DemoApplication.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DemoApplicationTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoApplicationTests.class);


    @RegisterExtension
    public final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);
    @Autowired
    private JavaMailSender sender;
    @Autowired
    private TransportPool pool;

    private static final int THREAD_COUNT = 10;

    @Test
    public void testConcurrentSend() throws Exception {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("thomas@voelk.org");
        message.setTo("thomas@voelk.org");
        message.setSubject("subject");
        message.setText("text");
        final AtomicInteger counter = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        int mailsPerThread = 200;
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                for (int m = 0; m < mailsPerThread; m++) {
                    sender.send(message);
                    counter.incrementAndGet();
                }
                return null;
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertEquals(THREAD_COUNT * mailsPerThread, counter.get());
        assertEquals(pool.getMaxTotal(), pool.getCreatedCount());

        waitForMessagesCount(THREAD_COUNT * mailsPerThread);
        assertEquals(THREAD_COUNT * mailsPerThread, getImapHostManager().getAllMessages().size());

    }

    @Test
    void name() throws AddressException {

        EmailAddress a = new EmailAddress(null);
    }

    protected void waitForMessagesCount(int count) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (getImapHostManager().getAllMessages().size() < count
                && (System.currentTimeMillis() - start) < 500) {
            Thread.sleep(50l);
        }
    }

    protected ImapHostManager getImapHostManager() {
        return greenMail.getManagers().getImapHostManager();
    }
}
